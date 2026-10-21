package com.timeline.service;

import com.timeline.record.DateValueRecord;
import com.timeline.record.PersonRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WikimediaService {

    private final WebClient wikipedia = WebClient.builder()
      .codecs(configurer ->
        configurer.defaultCodecs()
          .maxInMemorySize(10 * 1024 * 1024))
      .baseUrl("https://en.wikipedia.org")
      .defaultHeader("User-Agent", "HistoricalTimeline/1.0")
      .build();

    private final WebClient wikidata = WebClient.builder()
      .codecs(configurer ->
        configurer.defaultCodecs()
          .maxInMemorySize(10 * 1024 * 1024))
      .baseUrl("https://www.wikidata.org")
      .defaultHeader("User-Agent", "HistoricalTimeline/1.0")
      .build();

    private final ObjectMapper objectMapper;

    public List<PersonRecord> search(String query){
        if(query == null || query.isBlank()) return List.of();
        try {
            String encoded = UriUtils.encodeQueryParam(query.trim(), StandardCharsets.UTF_8);
            JsonNode root = wikipedia.get().uri("/w/rest.php/v1/search/page?q=" + encoded + "&limit=8")
              .retrieve().bodyToMono(JsonNode.class).block();
            List<PersonRecord> results = new ArrayList<>();
            JsonNode pages = root == null ? null : root.path("pages");
            if(pages != null && pages.isArray()){
                for(JsonNode page : pages){
                    String title = page.path("title").asText("");
                    results.add(new PersonRecord(title, page.path("description").asText(""), page.path("excerpt").asText(""),
                      page.path("thumbnail").path("url").asText(null), "https://en.wikipedia.org/wiki/" + title.replace(" ", "_"),
                      null, null, null, false, false));
                }
            }
            return results;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível pesquisar na Wikipedia agora.");
        }
    }

    public PersonRecord getPerson(String title){
        if(title == null || title.isBlank()) throw new IllegalArgumentException("Informe o título de uma figura histórica");

        try {
            title = Normalizer
              .normalize(title, Normalizer.Form.NFD)
              .replaceAll("\\p{M}", "");

            String encoded = UriUtils.encodePathSegment(title.replace(' ', '_'), StandardCharsets.UTF_8);
            JsonNode summary = wikipedia.get().uri("/api/rest_v1/page/summary/" + encoded)
              .retrieve().bodyToMono(JsonNode.class).block();
            if(summary == null || summary.path("title").isMissingNode()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Figura histórica não encontrada");
            String normalizedTitle = summary.path("title").asText(title);
            String qid = summary.path("wikibase_item").asText("");
            Integer birth = null, death = null;
            boolean birthApproximate = false, deathApproximate = false;

            if(!qid.isBlank()){
                JsonNode entity = wikidata.get().uri("/wiki/Special:EntityData/" + qid + ".json")
                  .retrieve().bodyToMono(JsonNode.class).block()
                  .path("entities").path(qid).path("claims");
                DateValueRecord b = extractYear(entity.path("P569"));
                DateValueRecord d = extractYear(entity.path("P570"));
                birth = b.year();
                death = d.year();
                birthApproximate = b.approximate();
                deathApproximate = d.approximate();
            }
            return new PersonRecord(normalizedTitle, summary.path("description").asText(""), summary.path("extract").asText(""),
              summary.path("thumbnail").path("source").asText(null),
              summary.path("content_urls").path("desktop").path("page").asText("https://en.wikipedia.org/wiki" + normalizedTitle.replace(" ", "_")),
              qid.isBlank() ? null : qid, birth, death, birthApproximate, deathApproximate);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e){
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao obter datas na Wikimedia");
        }
    }

    private DateValueRecord extractYear(JsonNode claims){
        if(claims == null || !claims.isArray() || claims.isNull()) return new DateValueRecord(null, false);
        JsonNode mainsnak = claims.get(0).path("mainsnak").path("datavalue").path("value");
        String time = mainsnak.path("time").asText("");
        if(time.length() < 5) return new DateValueRecord(null, false);
        try {
            int year = Integer.parseInt(time.substring(1, 5));
            if(time.startsWith("-")) year = -year;
            String precision = mainsnak.path("precision").asText("9");
            boolean approximate = !precision.equals("9") && !precision.equals("10") && !precision.equals("11");
            return new DateValueRecord(year, approximate);
        } catch (NumberFormatException e) {
            return new DateValueRecord(null, false);
        }
    }
}
