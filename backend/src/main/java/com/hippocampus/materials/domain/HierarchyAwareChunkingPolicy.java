package com.hippocampus.materials.domain;

import java.util.ArrayList; import java.util.HashSet; import java.util.List; import java.util.Set; import java.util.UUID;

public final class HierarchyAwareChunkingPolicy {
    private final ChunkTokenCounter counter; private final int target; private final int hard; private final int overlap;
    public HierarchyAwareChunkingPolicy(ChunkTokenCounter counter, int target, int hard, int overlap) {
        this.counter = counter; if (target < 1 || hard < target || overlap < 0 || overlap >= target) throw new IllegalArgumentException("Invalid chunk limits");
        this.target=target; this.hard=hard; this.overlap=overlap;
    }
    public Session session(UUID version, ChunkingHierarchy hierarchy) { return new Session(version, hierarchy); }
    public final class Session {
        private final UUID version; private final ChunkingHierarchy hierarchy; private final List<ChunkDraft> ready = new ArrayList<>();
        private final List<Item> current = new ArrayList<>(); private Item trailing; private int index; private int currentTokens;
        Session(UUID version, ChunkingHierarchy hierarchy) { this.version=version; this.hierarchy=hierarchy; }
        public void accept(ChunkSourceUnit unit) {
            hierarchy.validate(unit.documentNodeId(), unit.page());
            for (ChunkSourceUnit fragment : split(unit)) add(fragment);
        }
        public List<ChunkDraft> drain() { List<ChunkDraft> out=List.copyOf(ready); ready.clear(); return out; }
        public List<ChunkDraft> finish() { flush(); return drain(); }
        private void add(ChunkSourceUnit unit) {
            int tokens=counter.count(unit.content()); if (tokens < 1) return;
            boolean boundary=!current.isEmpty() && (!current.getFirst().unit.documentNodeId().equals(unit.documentNodeId())
                    || current.getFirst().unit.extractionMethod()!=unit.extractionMethod() || current.getFirst().unit.contentType()!=unit.contentType());
            int separator=current.isEmpty()?0:counter.count("\n\n");
            if (boundary || Math.addExact(Math.addExact(currentTokens, separator), tokens)>target) flush();
            if (current.isEmpty() && unit.contentType()==ChunkContentType.TEXT && trailing!=null && !trailing.unit.fragment()
                    && trailing.tokens<=overlap && trailing.unit.documentNodeId().equals(unit.documentNodeId())
                    && trailing.unit.extractionMethod()==unit.extractionMethod()) { current.add(new Item(trailing.unit,trailing.tokens,true)); currentTokens=trailing.tokens; }
            separator=current.isEmpty()?0:counter.count("\n\n");
            if (Math.addExact(Math.addExact(currentTokens, separator),tokens)>hard) { flush(); }
            current.add(new Item(unit,tokens,false)); currentTokens=Math.addExact(currentTokens, Math.addExact(current.isEmpty()?0:separator,tokens));
            if (unit.contentType()==ChunkContentType.TABLE) flush();
        }
        private void flush() {
            if (current.stream().noneMatch(i -> !i.overlap)) { current.clear(); currentTokens=0; return; }
            ChunkSourceUnit first=current.getFirst().unit; StringBuilder content=new StringBuilder(); List<ChunkDraft.SourceLink> links=new ArrayList<>();
            int start=Integer.MAX_VALUE,end=0,pos=0; long order=Long.MAX_VALUE; TextBlockQuality quality=null; Set<Integer> primaryPages=new HashSet<>();
            for(Item item:current){ if(!content.isEmpty()) content.append("\n\n"); content.append(item.unit.content()); links.add(new ChunkDraft.SourceLink(item.unit.textBlockId(),++pos,item.overlap));
                if(!item.overlap){start=Math.min(start,item.unit.page());end=Math.max(end,item.unit.page());order=Math.min(order,item.unit.sourceOrder());primaryPages.add(item.unit.page());}
                quality=worst(quality,item.unit.quality()); }
            int chunkIndex=++index; ready.add(new ChunkDraft(ChunkIdentity.forChunk(version,chunkIndex),version,first.documentNodeId(),chunkIndex,content.toString(),counter.count(content.toString()),start,end,
                    hierarchy.headingPath(first.documentNodeId()),first.contentType(),first.extractionMethod(),quality,order,List.copyOf(links),List.of()));
            Item last=current.getLast(); trailing=last.overlap?null:last; current.clear(); currentTokens=0;
            if(first.contentType()!=ChunkContentType.TEXT) trailing=null;
        }
        private List<ChunkSourceUnit> split(ChunkSourceUnit u) {
            if(counter.count(u.content())<=hard) return List.of(u); List<ChunkSourceUnit> out=new ArrayList<>(); int begin=0;
            while(begin<u.content().length()){ int end=fit(u.content(),begin,u.contentType()); if(end<=begin) throw new IllegalArgumentException("Unable to split source unit");
                out.add(new ChunkSourceUnit(u.textBlockId(),u.materialVersionId(),u.documentNodeId(),u.page(),u.contentType(),u.extractionMethod(),u.quality(),u.content().substring(begin,end),u.sourceOrder(),true)); begin=end; }
            return out;
        }
        private int fit(String s,int begin,ChunkContentType type){ int end=begin,bestSentence=-1,bestWhitespace=-1,bestTable=-1;
            while(end<s.length()){ int next=end+Character.charCount(s.codePointAt(end)); if(counter.count(s.substring(begin,next))>hard) break;
                int cp=s.codePointAt(end); if(cp=='\n'||cp=='\t') bestTable=next; if(Character.isWhitespace(cp))bestWhitespace=next; if(cp=='.'||cp=='!'||cp=='?')bestSentence=next; end=next; }
            if(end==s.length())return end; if(type==ChunkContentType.TABLE && bestTable>begin)return bestTable; if(bestSentence>begin)return bestSentence; if(bestWhitespace>begin)return bestWhitespace; return end; }
        private TextBlockQuality worst(TextBlockQuality a,TextBlockQuality b){if(a==null)return b;if(b==null)return a;return rank(a)>=rank(b)?a:b;}
        private int rank(TextBlockQuality q){return switch(q){case STRONG->0;case LIMITED->1;case POOR->2;};}
        private record Item(ChunkSourceUnit unit,int tokens,boolean overlap){}
    }
}
