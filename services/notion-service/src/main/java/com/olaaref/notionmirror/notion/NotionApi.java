package com.olaaref.notionmirror.notion;

import com.olaaref.notionmirror.notion.NotionModels.Page;
import com.olaaref.notionmirror.notion.NotionModels.PageMarkdown;
import com.olaaref.notionmirror.notion.NotionModels.SearchResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;

/** Declarative Notion HTTP client (Spring HTTP service interface). */
@HttpExchange(accept = "application/json", contentType = "application/json")
public interface NotionApi {

    @GetExchange("/v1/users/me")
    Map<String, Object> me();

    @PostExchange("/v1/search")
    SearchResponse search(@RequestBody Map<String, Object> body);

    @GetExchange("/v1/pages/{id}")
    Page getPage(@PathVariable String id);

    @GetExchange("/v1/pages/{id}/markdown?markdown_version={version}")
    PageMarkdown getMarkdown(@PathVariable String id, @PathVariable String version);

    /** Body: optional parent ({page_id} or omitted for a workspace-level page), properties/markdown. */
    @PostExchange("/v1/pages")
    Page createPage(@RequestBody Map<String, Object> body);

    /** Replace or edit a page's content as Markdown. The response can carry a "warnings" array. */
    @PatchExchange("/v1/pages/{id}/markdown")
    Map<String, Object> updateMarkdown(@PathVariable String id, @RequestBody Map<String, Object> body);

    @PostExchange("/v1/file_uploads")
    Map<String, Object> createFileUpload(@RequestBody Map<String, Object> body);

    @PatchExchange("/v1/blocks/{id}/children")
    Map<String, Object> appendChildren(@PathVariable String id, @RequestBody Map<String, Object> body);

    @GetExchange("/v1/blocks/{id}/children?page_size=100")
    Map<String, Object> listChildren(@PathVariable String id);

    @GetExchange("/v1/blocks/{id}/children?page_size=100&start_cursor={cursor}")
    Map<String, Object> listChildrenFrom(@PathVariable String id, @PathVariable String cursor);

    @PatchExchange("/v1/pages/{id}")
    Page updatePage(@PathVariable String id, @RequestBody Map<String, Object> body);
}
