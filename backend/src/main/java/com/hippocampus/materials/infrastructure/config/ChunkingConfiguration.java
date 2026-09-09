package com.hippocampus.materials.infrastructure.config;
import tools.jackson.databind.ObjectMapper; import org.springframework.boot.autoconfigure.AutoConfiguration; import org.springframework.boot.autoconfigure.condition.ConditionalOnBean; import org.springframework.boot.context.properties.EnableConfigurationProperties; import org.springframework.context.annotation.Bean; import org.springframework.jdbc.core.simple.JdbcClient; import org.springframework.transaction.PlatformTransactionManager;
import com.hippocampus.materials.application.*; import com.hippocampus.materials.domain.*; import com.hippocampus.materials.infrastructure.persistence.JdbcChunkRepository;
@AutoConfiguration(after=TextNormalizationConfiguration.class) @ConditionalOnBean({JdbcClient.class,PlatformTransactionManager.class,PdfExtractionProperties.class,PdfStructureInspectionProperties.class}) @EnableConfigurationProperties(ChunkingProperties.class)
public class ChunkingConfiguration {
 @Bean JdbcChunkRepository chunkRepository(JdbcClient jdbc,ObjectMapper mapper){return new JdbcChunkRepository(jdbc,mapper);}
 @Bean PersistChunkBatch persistChunkBatch(JdbcChunkRepository r){return new PersistChunkBatch(r);}@Bean FinalizeChunking finalizeChunking(JdbcChunkRepository r){return new FinalizeChunking(r);}
 @Bean ChunkMaterialText chunkMaterialText(JdbcChunkRepository r,PersistChunkBatch p,FinalizeChunking f,PdfExtractionProperties pdf,PdfStructureInspectionProperties structure,ChunkingProperties c){return new ChunkMaterialText(r,new HierarchyAwareChunkingPolicy(new DeterministicChunkTokenCounter(),c.targetTokenCount(),c.hardTokenCount(),c.overlapTokenCount()),p,f,pdf.pageBatchSize(),c.persistenceBatchSize(),structure.maxNodesPerDocument(),structure.maxOutlineDepth());}
 @Bean ProcessingStageHandler chunkMaterialStageHandler(ChunkMaterialText c){return new ChunkMaterialStageHandler(c);}
}
