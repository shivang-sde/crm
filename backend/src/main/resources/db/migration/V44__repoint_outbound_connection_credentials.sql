-- Outbound HTTP connection credentials now live in their own table
-- (outbound_http_connection_credentials) because ConnectorCredential rows are
-- bound to connector instances. No production rows existed for this column.
ALTER TABLE outbound_http_connections DROP CONSTRAINT fk_outbound_http_connections_credential;
ALTER TABLE outbound_http_connections
    ADD CONSTRAINT fk_outbound_http_connections_credential
    FOREIGN KEY (credential_id) REFERENCES outbound_http_connection_credentials (id);
