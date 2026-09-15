ALTER TABLE material_topic_links
    DROP CONSTRAINT chk_material_topic_links_document_node_phase2_disabled;

ALTER TABLE material_topic_links
    ADD CONSTRAINT fk_material_topic_links_node_same_version
    FOREIGN KEY (document_node_id, material_version_id)
    REFERENCES document_nodes (id, material_version_id)
    ON DELETE RESTRICT;
