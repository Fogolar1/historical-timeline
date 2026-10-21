package com.timeline.api;

import com.timeline.record.PersonRecord;
import com.timeline.record.TimelineRequestRecord;
import com.timeline.record.TimelineResponseRecord;
import com.timeline.service.WikimediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PeopleController {

    private final WikimediaService wikimediaService;

    @GetMapping("people/search")
    public List<PersonRecord> getPeopleSearch(@RequestParam("q") String query) {
        return wikimediaService.search(query);
    }

    @GetMapping("/people/{title}")
    public PersonRecord details(@PathVariable("title") String title) {
        return wikimediaService.getPerson(title);
    }

    @PostMapping("/timeline")
    public TimelineResponseRecord timeline(@Valid @RequestBody TimelineRequestRecord timelineRequestRecord) {
        if(timelineRequestRecord == null || timelineRequestRecord.titles().isEmpty() || timelineRequestRecord.titles().size() > 5)
          throw new IllegalArgumentException("Selecione entre 1 e 5 figuras históricas");

        List<PersonRecord> people = timelineRequestRecord.titles().stream().distinct().map(wikimediaService::getPerson).toList();
        return TimelineResponseRecord.from(people);
    }

}
