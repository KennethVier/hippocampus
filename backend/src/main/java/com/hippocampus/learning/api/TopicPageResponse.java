package com.hippocampus.learning.api;
import java.util.List; import com.hippocampus.learning.application.TopicPageResult;
public record TopicPageResponse(List<TopicResponse> items,int page,int size,long totalElements,int totalPages) {
    public TopicPageResponse { items=List.copyOf(items); }
    static TopicPageResponse from(TopicPageResult r) { return new TopicPageResponse(r.items().stream().map(TopicResponse::from).toList(),r.page(),r.size(),r.totalElements(),r.totalPages()); }
}
