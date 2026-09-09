package com.hippocampus.materials.application;

import java.util.ArrayList; import java.util.Comparator; import java.util.HashMap; import java.util.List; import java.util.Map; import java.util.Objects; import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.hippocampus.materials.domain.*; import com.hippocampus.materials.port.ChunkingSourceRepository;

public final class ChunkMaterialText {
    private final ChunkingSourceRepository sources; private final HierarchyAwareChunkingPolicy policy; private final PersistChunkBatch persistence; private final FinalizeChunking finalization;
    private final int pageBatchSize,persistenceBatchSize,maxNodes,maxDepth;
    public ChunkMaterialText(ChunkingSourceRepository s,HierarchyAwareChunkingPolicy p,PersistChunkBatch w,FinalizeChunking f,int pageBatchSize,int persistenceBatchSize,int maxNodes,int maxDepth){
        sources=Objects.requireNonNull(s);policy=Objects.requireNonNull(p);persistence=Objects.requireNonNull(w);finalization=Objects.requireNonNull(f);
        if(pageBatchSize<1||persistenceBatchSize<1)throw new IllegalArgumentException("Batch sizes must be positive");this.pageBatchSize=pageBatchSize;this.persistenceBatchSize=persistenceBatchSize;this.maxNodes=maxNodes;this.maxDepth=maxDepth;}
    public void execute(UUID version){ Objects.requireNonNull(version);if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Chunk orchestration must not be transactional");
        int pages=sources.requirePageCount(version); ChunkingHierarchy hierarchy=new ChunkingHierarchy(version,pages,sources.findHierarchy(version),maxNodes,maxDepth); var session=policy.session(version,hierarchy);
        List<ChunkDraft> expected=new ArrayList<>(); long order=0;
        for(int first=1;first<=pages;first+=pageBatchSize){int last=Math.min(pages,first+pageBatchSize-1);List<TextBlock> blocks=sources.findByPhysicalPage(version,first,last);
            blocks.sort(Comparator.comparing(TextBlock::pageNumber).thenComparing(b->b.blockType()==TextBlockType.PAGE_TEXT?0:1).thenComparingInt(TextBlock::ordinal));
            Map<Integer,List<ChunkingSourceRepository.VisualSource>> visuals=new HashMap<>();for(var v:sources.findVisualsByPhysicalPage(version,first,last))visuals.computeIfAbsent(v.pageNumber(),x->new ArrayList<>()).add(v);
            for(TextBlock b:blocks){validateSource(version,b); if(b.blockType()==TextBlockType.PAGE_TEXT){for(String paragraph:paragraphs(b.normalizedContent())){order=Math.addExact(order,1);session.accept(unit(b,paragraph,order));}}
                else if(b.blockType()==TextBlockType.TABLE_TEXT){order=Math.addExact(order,1);session.accept(unit(b,b.normalizedContent(),order));} else throw new IllegalStateException("Unsupported normalized source type"); drain(session,expected,visuals);}
            drain(session,expected,visuals);
        }
        for(ChunkDraft d:session.finish())add(expected,withVisuals(d,sources.findVisualsByPhysicalPage(version,d.pageStart(),d.pageEnd())));
        flush(expected); finalization.execute(version,pages,List.copyOf(expected));
    }
    private void drain(HierarchyAwareChunkingPolicy.Session s,List<ChunkDraft> expected,Map<Integer,List<ChunkingSourceRepository.VisualSource>> visuals){for(ChunkDraft d:s.drain())add(expected,withVisuals(d,visuals.values().stream().flatMap(List::stream).toList()));}
    private void add(List<ChunkDraft> expected,ChunkDraft d){expected.add(d);if(expected.size()%persistenceBatchSize==0)persistence.execute(d.materialVersionId(),expected.subList(expected.size()-persistenceBatchSize,expected.size()));}
    private void flush(List<ChunkDraft> all){int remainder=all.size()%persistenceBatchSize;if(remainder>0)persistence.execute(all.getFirst().materialVersionId(),all.subList(all.size()-remainder,all.size()));}
    private ChunkDraft withVisuals(ChunkDraft d,List<ChunkingSourceRepository.VisualSource> all){List<UUID> ids=all.stream().filter(v->v.materialVersionId().equals(d.materialVersionId())&&v.documentNodeId().equals(d.documentNodeId())&&v.pageNumber()>=d.pageStart()&&v.pageNumber()<=d.pageEnd()).map(ChunkingSourceRepository.VisualSource::id).sorted().toList();return new ChunkDraft(d.id(),d.materialVersionId(),d.documentNodeId(),d.chunkIndex(),d.content(),d.tokenCount(),d.pageStart(),d.pageEnd(),d.headingPath(),d.contentType(),d.extractionMethod(),d.quality(),d.sourceOrder(),d.sourceLinks(),ids);}
    private ChunkSourceUnit unit(TextBlock b,String content,long order){return new ChunkSourceUnit(b.id(),b.materialVersionId(),b.documentNodeId(),b.pageNumber(),b.blockType()==TextBlockType.PAGE_TEXT?ChunkContentType.TEXT:ChunkContentType.TABLE,b.extractionMethod(),b.quality(),content,order,false);}
    private void validateSource(UUID v,TextBlock b){if(!v.equals(b.materialVersionId())||b.documentNodeId()==null||b.pageNumber()==null||b.normalizedContent()==null)throw new IllegalStateException("Invalid normalized source provenance");if(b.blockType()==TextBlockType.TABLE_TEXT&&!b.content().equals(b.normalizedContent()))throw new IllegalStateException("Normalized table conflicts with raw source");}
    static List<String> paragraphs(String text){List<String> out=new ArrayList<>();int start=0,lineStart=0;boolean previousBlank=false;for(int i=0;i<=text.length();i++){boolean end=i==text.length();if(!end&&text.charAt(i)!='\n')continue;boolean blank=text.substring(lineStart,i).codePoints().allMatch(Character::isWhitespace);if(blank&&!previousBlank){String p=text.substring(start,lineStart);if(!p.isBlank())out.add(stripTerminalNewline(p));}if(blank)previousBlank=true;else{if(previousBlank)start=lineStart;previousBlank=false;}lineStart=i+1;}if(start<text.length()&&!previousBlank){String p=text.substring(start);if(!p.isBlank())out.add(p);}return out;}
    private static String stripTerminalNewline(String s){return s.endsWith("\n")?s.substring(0,s.length()-1):s;}
}
