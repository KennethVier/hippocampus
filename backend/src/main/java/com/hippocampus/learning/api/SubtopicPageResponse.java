package com.hippocampus.learning.api;
import java.util.List; import com.hippocampus.learning.application.SubtopicPageResult;
public record SubtopicPageResponse(List<SubtopicResponse> items,int page,int size,long totalElements,int totalPages) {
    public SubtopicPageResponse { items=List.copyOf(items); }
    static SubtopicPageResponse from(SubtopicPageResult r) { return new SubtopicPageResponse(r.items().stream().map(SubtopicResponse::from).toList(),r.page(),r.size(),r.totalElements(),r.totalPages()); }
}
