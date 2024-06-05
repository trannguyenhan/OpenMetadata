/*
 *  Copyright 2021 Collate
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openmetadata.service.jdbi3;

import static org.openmetadata.schema.type.Relationship.CONTAINS;
import static org.openmetadata.schema.type.Relationship.MENTIONED_IN;
import static org.openmetadata.service.Entity.ORGANIZATION_NAME;
import static org.openmetadata.service.Entity.QUERY;
import static org.openmetadata.service.jdbi3.ListFilter.escapeApostrophe;
import static org.openmetadata.service.jdbi3.locator.ConnectionType.MYSQL;
import static org.openmetadata.service.jdbi3.locator.ConnectionType.POSTGRES;

import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.core.statement.StatementException;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBeanList;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindMap;
import org.jdbi.v3.sqlobject.customizer.Define;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.openmetadata.api.configuration.LogoConfiguration;
import org.openmetadata.common.utils.CommonUtil;
import org.openmetadata.schema.TokenInterface;
import org.openmetadata.schema.analytics.ReportData;
import org.openmetadata.schema.analytics.WebAnalyticEvent;
import org.openmetadata.schema.auth.EmailVerificationToken;
import org.openmetadata.schema.auth.PasswordResetToken;
import org.openmetadata.schema.auth.PersonalAccessToken;
import org.openmetadata.schema.auth.RefreshToken;
import org.openmetadata.schema.auth.TokenType;
import org.openmetadata.schema.dataInsight.DataInsightChart;
import org.openmetadata.schema.dataInsight.kpi.Kpi;
import org.openmetadata.schema.email.SmtpSettings;
import org.openmetadata.schema.entity.Bot;
import org.openmetadata.schema.entity.Type;
import org.openmetadata.schema.entity.automations.Workflow;
import org.openmetadata.schema.entity.classification.Classification;
import org.openmetadata.schema.entity.classification.Tag;
import org.openmetadata.schema.entity.data.Chart;
import org.openmetadata.schema.entity.data.Container;
import org.openmetadata.schema.entity.data.Dashboard;
import org.openmetadata.schema.entity.data.DashboardDataModel;
import org.openmetadata.schema.entity.data.Database;
import org.openmetadata.schema.entity.data.DatabaseSchema;
import org.openmetadata.schema.entity.data.Glossary;
import org.openmetadata.schema.entity.data.GlossaryTerm;
import org.openmetadata.schema.entity.data.Metrics;
import org.openmetadata.schema.entity.data.MlModel;
import org.openmetadata.schema.entity.data.Pipeline;
import org.openmetadata.schema.entity.data.Query;
import org.openmetadata.schema.entity.data.Report;
import org.openmetadata.schema.entity.data.Table;
import org.openmetadata.schema.entity.data.Topic;
import org.openmetadata.schema.entity.events.EventSubscription;
import org.openmetadata.schema.entity.policies.Policy;
import org.openmetadata.schema.entity.services.DashboardService;
import org.openmetadata.schema.entity.services.DatabaseService;
import org.openmetadata.schema.entity.services.MessagingService;
import org.openmetadata.schema.entity.services.MetadataService;
import org.openmetadata.schema.entity.services.MlModelService;
import org.openmetadata.schema.entity.services.PipelineService;
import org.openmetadata.schema.entity.services.StorageService;
import org.openmetadata.schema.entity.services.connections.TestConnectionDefinition;
import org.openmetadata.schema.entity.services.ingestionPipelines.IngestionPipeline;
import org.openmetadata.schema.entity.teams.Role;
import org.openmetadata.schema.entity.teams.Team;
import org.openmetadata.schema.entity.teams.User;
import org.openmetadata.schema.settings.Settings;
import org.openmetadata.schema.settings.SettingsType;
import org.openmetadata.schema.tests.TestCase;
import org.openmetadata.schema.tests.TestDefinition;
import org.openmetadata.schema.tests.TestSuite;
import org.openmetadata.schema.type.Include;
import org.openmetadata.schema.type.Relationship;
import org.openmetadata.schema.type.TagLabel;
import org.openmetadata.schema.type.TaskStatus;
import org.openmetadata.schema.type.ThreadType;
import org.openmetadata.schema.type.UsageDetails;
import org.openmetadata.schema.type.UsageStats;
import org.openmetadata.schema.util.EntitiesCount;
import org.openmetadata.schema.util.ServicesCount;
import org.openmetadata.schema.utils.EntityInterfaceUtil;
import org.openmetadata.service.Entity;
import org.openmetadata.service.jdbi3.CollectionDAO.TagUsageDAO.TagLabelMapper;
import org.openmetadata.service.jdbi3.CollectionDAO.UsageDAO.UsageDetailsMapper;
import org.openmetadata.service.jdbi3.FeedRepository.FilterType;
import org.openmetadata.service.jdbi3.locator.ConnectionAwareSqlQuery;
import org.openmetadata.service.jdbi3.locator.ConnectionAwareSqlUpdate;
import org.openmetadata.service.resources.feeds.MessageParser.EntityLink;
import org.openmetadata.service.resources.tags.TagLabelUtil;
import org.openmetadata.service.util.EntityUtil;
import org.openmetadata.service.util.FullyQualifiedName;
import org.openmetadata.service.util.JsonUtils;
import org.openmetadata.service.util.jdbi.BindFQN;

public interface CollectionDAO {
  @CreateSqlObject
  DatabaseDAO databaseDAO();

  @CreateSqlObject
  DatabaseSchemaDAO databaseSchemaDAO();

  @CreateSqlObject
  EntityRelationshipDAO relationshipDAO();

  @CreateSqlObject
  FieldRelationshipDAO fieldRelationshipDAO();

  @CreateSqlObject
  EntityExtensionDAO entityExtensionDAO();

  @CreateSqlObject
  EntityExtensionTimeSeriesDAO entityExtensionTimeSeriesDao();

  @CreateSqlObject
  RoleDAO roleDAO();

  @CreateSqlObject
  UserDAO userDAO();

  @CreateSqlObject
  TeamDAO teamDAO();

  @CreateSqlObject
  TagUsageDAO tagUsageDAO();

  @CreateSqlObject
  TagDAO tagDAO();

  @CreateSqlObject
  ClassificationDAO classificationDAO();

  @CreateSqlObject
  TableDAO tableDAO();

  @CreateSqlObject
  UsageDAO usageDAO();

  @CreateSqlObject
  MetricsDAO metricsDAO();

  @CreateSqlObject
  ChartDAO chartDAO();

  @CreateSqlObject
  PipelineDAO pipelineDAO();

  @CreateSqlObject
  DashboardDAO dashboardDAO();

  @CreateSqlObject
  ReportDAO reportDAO();

  @CreateSqlObject
  TopicDAO topicDAO();

  @CreateSqlObject
  MlModelDAO mlModelDAO();

  @CreateSqlObject
  GlossaryDAO glossaryDAO();

  @CreateSqlObject
  GlossaryTermDAO glossaryTermDAO();

  @CreateSqlObject
  BotDAO botDAO();

  @CreateSqlObject
  EventSubscriptionDAO eventSubscriptionDAO();

  @CreateSqlObject
  PolicyDAO policyDAO();

  @CreateSqlObject
  IngestionPipelineDAO ingestionPipelineDAO();

  @CreateSqlObject
  DatabaseServiceDAO dbServiceDAO();

  @CreateSqlObject
  MetadataServiceDAO metadataServiceDAO();

  @CreateSqlObject
  PipelineServiceDAO pipelineServiceDAO();

  @CreateSqlObject
  MlModelServiceDAO mlModelServiceDAO();

  @CreateSqlObject
  DashboardServiceDAO dashboardServiceDAO();

  @CreateSqlObject
  MessagingServiceDAO messagingServiceDAO();

  @CreateSqlObject
  StorageServiceDAO storageServiceDAO();

  @CreateSqlObject
  ContainerDAO containerDAO();

  @CreateSqlObject
  FeedDAO feedDAO();

  @CreateSqlObject
  QueryDAO queryDAO();

  @CreateSqlObject
  ChangeEventDAO changeEventDAO();

  @CreateSqlObject
  TypeEntityDAO typeEntityDAO();

  @CreateSqlObject
  TestDefinitionDAO testDefinitionDAO();

  @CreateSqlObject
  TestConnectionDefinitionDAO testConnectionDefinitionDAO();

  @CreateSqlObject
  TestSuiteDAO testSuiteDAO();

  @CreateSqlObject
  TestCaseDAO testCaseDAO();

  @CreateSqlObject
  WebAnalyticEventDAO webAnalyticEventDAO();

  @CreateSqlObject
  DataInsightChartDAO dataInsightChartDAO();

  @CreateSqlObject
  SystemDAO systemDAO();

  @CreateSqlObject
  TokenDAO getTokenDAO();

  @CreateSqlObject
  KpiDAO kpiDAO();

  @CreateSqlObject
  WorkflowDAO workflowDAO();

  @CreateSqlObject
  DataModelDAO dashboardDataModelDAO();

  interface DashboardDAO extends EntityDAO<Dashboard> {
    @Override
    default String getTableName() {
      return "dashboard_entity";
    }

    @Override
    default Class<Dashboard> getEntityClass() {
      return Dashboard.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }

  interface DashboardServiceDAO extends EntityDAO<DashboardService> {
    @Override
    default String getTableName() {
      return "dashboard_service_entity";
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }

    @Override
    default Class<DashboardService> getEntityClass() {
      return DashboardService.class;
    }
  }

  interface DatabaseDAO extends EntityDAO<Database> {
    @Override
    default String getTableName() {
      return "database_entity";
    }

    @Override
    default Class<Database> getEntityClass() {
      return Database.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }

  interface DatabaseSchemaDAO extends EntityDAO<DatabaseSchema> {
    @Override
    default String getTableName() {
      return "database_schema_entity";
    }

    @Override
    default Class<DatabaseSchema> getEntityClass() {
      return DatabaseSchema.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }

  interface DatabaseServiceDAO extends EntityDAO<DatabaseService> {
    @Override
    default String getTableName() {
      return "dbservice_entity";
    }

    @Override
    default Class<DatabaseService> getEntityClass() {
      return DatabaseService.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }
  }

  interface MetadataServiceDAO extends EntityDAO<MetadataService> {
    @Override
    default String getTableName() {
      return "metadata_service_entity";
    }

    @Override
    default Class<MetadataService> getEntityClass() {
      return MetadataService.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }
  }

  interface TestConnectionDefinitionDAO extends EntityDAO<TestConnectionDefinition> {
    @Override
    default String getTableName() {
      return "test_connection_definition";
    }

    @Override
    default Class<TestConnectionDefinition> getEntityClass() {
      return TestConnectionDefinition.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }
  }

  interface StorageServiceDAO extends EntityDAO<StorageService> {
    @Override
    default String getTableName() {
      return "storage_service_entity";
    }

    @Override
    default Class<StorageService> getEntityClass() {
      return StorageService.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }
  }

  interface ContainerDAO extends EntityDAO<Container> {
    @Override
    default String getTableName() {
      return "storage_container_entity";
    }

    @Override
    default Class<Container> getEntityClass() {
      return Container.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }

    @Override
    default List<String> listBefore(ListFilter filter, int limit, String before) {
      boolean root = Boolean.parseBoolean(filter.getQueryParam("root"));
      String condition = filter.getCondition();

      // By default, root will be false. We won't filter the results then
      if (!root) {
        return EntityDAO.super.listBefore(filter, limit, before);
      }

      String sqlCondition = String.format("%s AND er.toId is NULL", condition);

      return listBefore(getTableName(), getNameColumn(), sqlCondition, limit, before);
    }

    @Override
    default List<String> listAfter(ListFilter filter, int limit, String after) {
      boolean root = Boolean.parseBoolean(filter.getQueryParam("root"));
      String condition = filter.getCondition();

      if (!root) {
        return EntityDAO.super.listAfter(filter, limit, after);
      }

      String sqlCondition = String.format("%s AND er.toId is NULL", condition);

      return listAfter(getTableName(), getNameColumn(), sqlCondition, limit, after);
    }

    @Override
    default int listCount(ListFilter filter) {
      boolean root = Boolean.parseBoolean(filter.getQueryParam("root"));
      String condition = filter.getCondition();

      if (!root) {
        return EntityDAO.super.listCount(filter);
      }

      String sqlCondition = String.format("%s AND er.toId is NULL", condition);

      return listCount(getTableName(), getNameColumn(), sqlCondition);
    }

    @SqlQuery(
        value =
            "SELECT json FROM ("
                + "SELECT <nameColumn>, ce.json FROM <table> ce "
                + "LEFT JOIN ("
                + "  SELECT toId FROM entity_relationship "
                + "  WHERE fromEntity = 'container' AND toEntity = 'container' AND relation = 0 "
                + ") er "
                + "on ce.id = er.toId "
                + "<sqlCondition> AND "
                + "<nameColumn> < :before "
                + "ORDER BY <nameColumn> DESC "
                + "LIMIT :limit"
                + ") last_rows_subquery ORDER BY <nameColumn>")
    List<String> listBefore(
        @Define("table") String table,
        @Define("nameColumn") String nameColumn,
        @Define("sqlCondition") String sqlCondition,
        @Bind("limit") int limit,
        @Bind("before") String before);

    @SqlQuery(
        value =
            "SELECT ce.json FROM <table> ce "
                + "LEFT JOIN ("
                + "  SELECT toId FROM entity_relationship "
                + "  WHERE fromEntity = 'container' AND toEntity = 'container' AND relation = 0 "
                + ") er "
                + "on ce.id = er.toId "
                + "<sqlCondition> AND "
                + "<nameColumn> > :after "
                + "ORDER BY <nameColumn> "
                + "LIMIT :limit")
    List<String> listAfter(
        @Define("table") String table,
        @Define("nameColumn") String nameColumn,
        @Define("sqlCondition") String sqlCondition,
        @Bind("limit") int limit,
        @Bind("after") String after);

    @SqlQuery(
        value =
            "SELECT count(*) FROM <table> ce "
                + "LEFT JOIN ("
                + "  SELECT toId FROM entity_relationship "
                + "  WHERE fromEntity = 'container' AND toEntity = 'container' AND relation = 0 "
                + ") er "
                + "on ce.id = er.toId "
                + "<sqlCondition>")
    int listCount(
        @Define("table") String table,
        @Define("nameColumn") String nameColumn,
        @Define("sqlCondition") String mysqlCond);
  }

  interface EntityExtensionDAO {
    @ConnectionAwareSqlUpdate(
        value =
            "REPLACE INTO entity_extension(id, extension, jsonSchema, json) "
                + "VALUES (:id, :extension, :jsonSchema, :json)",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO entity_extension(id, extension, jsonSchema, json) "
                + "VALUES (:id, :extension, :jsonSchema, (:json :: jsonb)) "
                + "ON CONFLICT (id, extension) DO UPDATE SET jsonSchema = EXCLUDED.jsonSchema, json = EXCLUDED.json",
        connectionType = POSTGRES)
    void insert(
        @Bind("id") String id,
        @Bind("extension") String extension,
        @Bind("jsonSchema") String jsonSchema,
        @Bind("json") String json);

    @SqlQuery("SELECT json FROM entity_extension WHERE id = :id AND extension = :extension")
    String getExtension(@Bind("id") String id, @Bind("extension") String extension);

    @RegisterRowMapper(ExtensionMapper.class)
    @SqlQuery(
        "SELECT extension, json FROM entity_extension WHERE id = :id AND extension "
            + "LIKE CONCAT (:extensionPrefix, '.%') "
            + "ORDER BY extension")
    List<ExtensionRecord> getExtensions(@Bind("id") String id, @Bind("extensionPrefix") String extensionPrefix);

    @SqlUpdate("DELETE FROM entity_extension WHERE id = :id AND extension = :extension")
    void delete(@Bind("id") String id, @Bind("extension") String extension);

    @SqlUpdate("DELETE FROM entity_extension WHERE extension = :extension")
    void deleteExtension(@Bind("extension") String extension);

    @SqlUpdate("DELETE FROM entity_extension WHERE id = :id")
    void deleteAll(@Bind("id") String id);
  }

  class EntityVersionPair {
    @Getter private final Double version;
    @Getter private final String entityJson;

    public EntityVersionPair(ExtensionRecord extensionRecord) {
      this.version = EntityUtil.getVersion(extensionRecord.getExtensionName());
      this.entityJson = extensionRecord.getExtensionJson();
    }
  }

  class ExtensionRecord {
    @Getter private final String extensionName;
    @Getter private final String extensionJson;

    public ExtensionRecord(String extensionName, String extensionJson) {
      this.extensionName = extensionName;
      this.extensionJson = extensionJson;
    }
  }

  class ExtensionMapper implements RowMapper<ExtensionRecord> {
    @Override
    public ExtensionRecord map(ResultSet rs, StatementContext ctx) throws SQLException {
      return new ExtensionRecord(rs.getString("extension"), rs.getString("json"));
    }
  }

  @Getter
  @Builder
  class EntityRelationshipRecord {
    private UUID id;
    private String type;
    private String json;
  }

  @Getter
  @Builder
  class EntityRelationshipObject {
    private String fromId;
    private String toId;
    private String fromEntity;
    private String toEntity;
    private int relation;
  }

  @Getter
  @Builder
  class ReportDataRow {
    private String rowNum;
    private ReportData reportData;
  }

  @Getter
  @Builder
  class QueryList {
    private String fqn;
    private Query query;
  }

  interface EntityRelationshipDAO {
    default void insert(UUID fromId, UUID toId, String fromEntity, String toEntity, int relation) {
      insert(fromId, toId, fromEntity, toEntity, relation, null);
    }

    default void insert(UUID fromId, UUID toId, String fromEntity, String toEntity, int relation, String json) {
      insert(fromId.toString(), toId.toString(), fromEntity, toEntity, relation, json);
    }

    default void bulkInsertToRelationship(
        UUID fromId, List<UUID> toIds, String fromEntity, String toEntity, int relation) {

      List<EntityRelationshipObject> insertToRelationship =
          toIds.stream()
              .map(
                  testCase ->
                      EntityRelationshipObject.builder()
                          .fromId(fromId.toString())
                          .toId(testCase.toString())
                          .fromEntity(fromEntity)
                          .toEntity(toEntity)
                          .relation(relation)
                          .build())
              .collect(Collectors.toList());

      bulkInsertTo(insertToRelationship);
    }

    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO entity_relationship(fromId, toId, fromEntity, toEntity, relation, json) "
                + "VALUES (:fromId, :toId, :fromEntity, :toEntity, :relation, :json) "
                + "ON DUPLICATE KEY UPDATE json = :json",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO entity_relationship(fromId, toId, fromEntity, toEntity, relation, json) VALUES "
                + "(:fromId, :toId, :fromEntity, :toEntity, :relation, (:json :: jsonb)) "
                + "ON CONFLICT (fromId, toId, relation) DO UPDATE SET json = EXCLUDED.json",
        connectionType = POSTGRES)
    void insert(
        @Bind("fromId") String fromId,
        @Bind("toId") String toId,
        @Bind("fromEntity") String fromEntity,
        @Bind("toEntity") String toEntity,
        @Bind("relation") int relation,
        @Bind("json") String json);

    @ConnectionAwareSqlUpdate(
        value = "INSERT IGNORE INTO entity_relationship(fromId, toId, fromEntity, toEntity, relation) VALUES <values>",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO entity_relationship(fromId, toId, fromEntity, toEntity, relation) VALUES <values>"
                + "ON CONFLICT DO NOTHING",
        connectionType = POSTGRES)
    void bulkInsertTo(
        @BindBeanList(
                value = "values",
                propertyNames = {"fromId", "toId", "fromEntity", "toEntity", "relation"})
            List<EntityRelationshipObject> values);

    //
    // Find to operations
    //
    @SqlQuery(
        "SELECT toId, toEntity, json FROM entity_relationship "
            + "WHERE fromId = :fromId AND fromEntity = :fromEntity AND relation IN (<relation>) "
            + "ORDER BY toId")
    @RegisterRowMapper(ToRelationshipMapper.class)
    List<EntityRelationshipRecord> findTo(
        @Bind("fromId") String fromId,
        @Bind("fromEntity") String fromEntity,
        @BindList("relation") List<Integer> relation);

    default List<EntityRelationshipRecord> findTo(String fromId, String fromEntity, int relation) {
      return findTo(fromId, fromEntity, List.of(relation));
    }

    // TODO delete this
    @SqlQuery(
        "SELECT toId, toEntity, json FROM entity_relationship "
            + "WHERE fromId = :fromId AND fromEntity = :fromEntity AND relation = :relation AND toEntity = :toEntity "
            + "ORDER BY toId")
    @RegisterRowMapper(ToRelationshipMapper.class)
    List<EntityRelationshipRecord> findTo(
        @Bind("fromId") String fromId,
        @Bind("fromEntity") String fromEntity,
        @Bind("relation") int relation,
        @Bind("toEntity") String toEntity);

    @ConnectionAwareSqlQuery(
        value =
            "SELECT toId, toEntity, json FROM entity_relationship "
                + "WHERE JSON_UNQUOTE(JSON_EXTRACT(json, '$.pipeline.id')) =:fromId OR fromId = :fromId AND relation = :relation "
                + "ORDER BY toId",
        connectionType = MYSQL)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT toId, toEntity, json FROM entity_relationship "
                + "WHERE  json->'pipeline'->>'id' =:fromId OR fromId = :fromId AND relation = :relation "
                + "ORDER BY toId",
        connectionType = POSTGRES)
    @RegisterRowMapper(ToRelationshipMapper.class)
    List<EntityRelationshipRecord> findToPipeline(@Bind("fromId") String fromId, @Bind("relation") int relation);

    //
    // Find from operations
    //
    @SqlQuery(
        "SELECT fromId, fromEntity, json FROM entity_relationship "
            + "WHERE toId = :toId AND toEntity = :toEntity AND relation = :relation AND fromEntity = :fromEntity "
            + "ORDER BY fromId")
    @RegisterRowMapper(FromRelationshipMapper.class)
    List<EntityRelationshipRecord> findFrom(
        @Bind("toId") String toId,
        @Bind("toEntity") String toEntity,
        @Bind("relation") int relation,
        @Bind("fromEntity") String fromEntity);

    @SqlQuery(
        "SELECT fromId, fromEntity, json FROM entity_relationship "
            + "WHERE toId = :toId AND toEntity = :toEntity AND relation = :relation "
            + "ORDER BY fromId")
    @RegisterRowMapper(FromRelationshipMapper.class)
    List<EntityRelationshipRecord> findFrom(
        @Bind("toId") String toId, @Bind("toEntity") String toEntity, @Bind("relation") int relation);

    @ConnectionAwareSqlQuery(
        value =
            "SELECT fromId, fromEntity, json FROM entity_relationship "
                + "WHERE JSON_UNQUOTE(JSON_EXTRACT(json, '$.pipeline.id')) = :toId OR toId = :toId AND relation = :relation "
                + "ORDER BY fromId",
        connectionType = MYSQL)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT fromId, fromEntity, json FROM entity_relationship "
                + "WHERE  json->'pipeline'->>'id' = :toId OR toId = :toId AND relation = :relation "
                + "ORDER BY fromId",
        connectionType = POSTGRES)
    @RegisterRowMapper(FromRelationshipMapper.class)
    List<EntityRelationshipRecord> findFromPipleine(@Bind("toId") String toId, @Bind("relation") int relation);

    @SqlQuery("SELECT fromId, fromEntity, json FROM entity_relationship " + "WHERE toId = :toId ORDER BY fromId")
    @RegisterRowMapper(FromRelationshipMapper.class)
    List<EntityRelationshipRecord> findFrom(@Bind("toId") String toId);

    @SqlQuery("SELECT count(*) FROM entity_relationship " + "WHERE fromEntity = :fromEntity AND toEntity = :toEntity")
    int findIfAnyRelationExist(@Bind("fromEntity") String fromEntity, @Bind("toEntity") String toEntity);

    //
    // Delete Operations
    //
    @SqlUpdate(
        "DELETE from entity_relationship WHERE fromId = :fromId "
            + "AND fromEntity = :fromEntity AND toId = :toId AND toEntity = :toEntity "
            + "AND relation = :relation")
    int delete(
        @Bind("fromId") String fromId,
        @Bind("fromEntity") String fromEntity,
        @Bind("toId") String toId,
        @Bind("toEntity") String toEntity,
        @Bind("relation") int relation);

    // Delete all the entity relationship fromID --- relation --> entity of type toEntity
    @SqlUpdate(
        "DELETE from entity_relationship WHERE fromId = :fromId AND fromEntity = :fromEntity "
            + "AND relation = :relation AND toEntity = :toEntity")
    void deleteFrom(
        @Bind("fromId") String fromId,
        @Bind("fromEntity") String fromEntity,
        @Bind("relation") int relation,
        @Bind("toEntity") String toEntity);

    // Delete all the entity relationship toId <-- relation --  entity of type fromEntity
    @SqlUpdate(
        "DELETE from entity_relationship WHERE toId = :toId AND toEntity = :toEntity AND relation = :relation "
            + "AND fromEntity = :fromEntity")
    void deleteTo(
        @Bind("toId") String toId,
        @Bind("toEntity") String toEntity,
        @Bind("relation") int relation,
        @Bind("fromEntity") String fromEntity);

    @SqlUpdate(
        "DELETE from entity_relationship WHERE (toId = :id AND toEntity = :entity) OR "
            + "(fromId = :id AND fromEntity = :entity)")
    void deleteAll(@Bind("id") String id, @Bind("entity") String entity);

    @SqlUpdate("DELETE from entity_relationship WHERE fromId = :id or toId = :id")
    void deleteAllWithId(@Bind("id") String id);

    class FromRelationshipMapper implements RowMapper<EntityRelationshipRecord> {
      @Override
      public EntityRelationshipRecord map(ResultSet rs, StatementContext ctx) throws SQLException {
        return EntityRelationshipRecord.builder()
            .id(UUID.fromString(rs.getString("fromId")))
            .type(rs.getString("fromEntity"))
            .json(rs.getString("json"))
            .build();
      }
    }

    class ToRelationshipMapper implements RowMapper<EntityRelationshipRecord> {
      @Override
      public EntityRelationshipRecord map(ResultSet rs, StatementContext ctx) throws SQLException {
        return EntityRelationshipRecord.builder()
            .id(UUID.fromString(rs.getString("toId")))
            .type(rs.getString("toEntity"))
            .json(rs.getString("json"))
            .build();
      }
    }
  }

  interface FeedDAO {
    @ConnectionAwareSqlUpdate(value = "INSERT INTO thread_entity(json) VALUES (:json)", connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value = "INSERT INTO thread_entity(json) VALUES (:json :: jsonb)",
        connectionType = POSTGRES)
    void insert(@Bind("json") String json);

    @SqlQuery("SELECT json FROM thread_entity WHERE id = :id")
    String findById(@Bind("id") String id);

    @SqlQuery("SELECT json FROM thread_entity ORDER BY createdAt DESC")
    List<String> list();

    @SqlQuery("SELECT count(id) FROM thread_entity <condition>")
    int listCount(@Define("condition") String condition);

    @SqlUpdate("DELETE FROM thread_entity WHERE id = :id")
    void delete(@Bind("id") String id);

    @ConnectionAwareSqlUpdate(value = "UPDATE task_sequence SET id=LAST_INSERT_ID(id+1)", connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(value = "UPDATE task_sequence SET id=(id+1) RETURNING id", connectionType = POSTGRES)
    void updateTaskId();

    @SqlQuery("SELECT id FROM task_sequence LIMIT 1")
    int getTaskId();

    @SqlQuery("SELECT json FROM thread_entity WHERE taskId = :id")
    String findByTaskId(@Bind("id") int id);

    @SqlQuery("SELECT json FROM thread_entity <condition> ORDER BY createdAt DESC LIMIT :limit")
    List<String> list(@Bind("limit") int limit, @Define("condition") String condition);

    @SqlQuery(
        "SELECT json FROM thread_entity "
            + "WHERE type='Announcement' AND (:threadId IS NULL OR id != :threadId) "
            + "AND entityId = :entityId "
            + "AND (( :startTs >= announcementStart AND :startTs < announcementEnd) "
            + "OR (:endTs > announcementStart AND :endTs < announcementEnd) "
            + "OR (:startTs <= announcementStart AND :endTs >= announcementEnd))")
    List<String> listAnnouncementBetween(
        @Bind("threadId") String threadId,
        @Bind("entityId") String entityId,
        @Bind("startTs") long startTs,
        @Bind("endTs") long endTs);

    @ConnectionAwareSqlQuery(
        value =
            "SELECT json FROM thread_entity <condition> AND "
                + "taskAssignees @> ANY (ARRAY[<userTeamJsonPostgres>]::jsonb[]) "
                + "ORDER BY createdAt DESC "
                + "LIMIT :limit",
        connectionType = POSTGRES)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT json FROM thread_entity <condition> AND "
                + "JSON_OVERLAPS(taskAssignees, :userTeamJsonMysql) "
                + "ORDER BY createdAt DESC "
                + "LIMIT :limit",
        connectionType = MYSQL)
    List<String> listTasksAssigned(
        @BindList("userTeamJsonPostgres") List<String> userTeamJsonPostgres,
        @Bind("userTeamJsonMysql") String userTeamJsonMysql,
        @Bind("limit") int limit,
        @Define("condition") String condition);

    @ConnectionAwareSqlQuery(
        value =
            "SELECT count(id) FROM thread_entity <condition> AND "
                + "taskAssignees @> ANY (ARRAY[<userTeamJsonPostgres>]::jsonb[])",
        connectionType = POSTGRES)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT count(id) FROM thread_entity <condition> AND "
                + "JSON_OVERLAPS(taskAssignees, :userTeamJsonMysql) ",
        connectionType = MYSQL)
    int listCountTasksAssignedTo(
        @BindList("userTeamJsonPostgres") List<String> userTeamJsonPostgres,
        @Bind("userTeamJsonMysql") String userTeamJsonMysql,
        @Define("condition") String condition);

    @ConnectionAwareSqlQuery(
        value =
            "SELECT json FROM thread_entity <condition> "
                + "AND (taskAssignees @> ANY (ARRAY[<userTeamJsonPostgres>]::jsonb[]) OR createdBy = :username) "
                + "ORDER BY createdAt DESC "
                + "LIMIT :limit",
        connectionType = POSTGRES)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT json FROM thread_entity <condition> "
                + "AND (JSON_OVERLAPS(taskAssignees, :userTeamJsonMysql) OR createdBy = :username) "
                + "ORDER BY createdAt DESC "
                + "LIMIT :limit",
        connectionType = MYSQL)
    List<String> listTasksOfUser(
        @BindList("userTeamJsonPostgres") List<String> userTeamJsonPostgres,
        @Bind("userTeamJsonMysql") String userTeamJsonMysql,
        @Bind("username") String username,
        @Bind("limit") int limit,
        @Define("condition") String condition);

    @ConnectionAwareSqlQuery(
        value =
            "SELECT count(id) FROM thread_entity <condition> "
                + "AND (taskAssignees @> ANY (ARRAY[<userTeamJsonPostgres>]::jsonb[]) OR createdBy = :username) ",
        connectionType = POSTGRES)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT count(id) FROM thread_entity <condition> "
                + "AND (JSON_OVERLAPS(taskAssignees, :userTeamJsonMysql) OR createdBy = :username) ",
        connectionType = MYSQL)
    int listCountTasksOfUser(
        @BindList("userTeamJsonPostgres") List<String> userTeamJsonPostgres,
        @Bind("userTeamJsonMysql") String userTeamJsonMysql,
        @Bind("username") String username,
        @Define("condition") String condition);

    @SqlQuery(
        "SELECT json FROM thread_entity <condition> AND createdBy = :username ORDER BY createdAt DESC LIMIT :limit")
    List<String> listTasksAssigned(
        @Bind("username") String username, @Bind("limit") int limit, @Define("condition") String condition);

    @SqlQuery("SELECT count(id) FROM thread_entity <condition> AND createdBy = :username")
    int listCountTasksAssignedBy(@Bind("username") String username, @Define("condition") String condition);

    @SqlQuery(
        "SELECT json FROM thread_entity <condition> AND "
            // Entity for which the thread is about is owned by the user or his teams
            + "(entityId in (SELECT toId FROM entity_relationship WHERE "
            + "((fromEntity='user' AND fromId= :userId) OR "
            + "(fromEntity='team' AND fromId IN (<teamIds>))) AND relation=8) OR "
            + "id in (SELECT toId FROM entity_relationship WHERE (fromEntity='user' AND fromId= :userId AND toEntity='THREAD' AND relation IN (1,2)))) "
            + "ORDER BY createdAt DESC "
            + "LIMIT :limit")
    List<String> listThreadsByOwner(
        @Bind("userId") String userId,
        @BindList("teamIds") List<String> teamIds,
        @Bind("limit") int limit,
        @Define("condition") String condition);

    @SqlQuery(
        "SELECT count(id) FROM thread_entity <condition> AND "
            + "(entityId in (SELECT toId FROM entity_relationship WHERE "
            + "((fromEntity='user' AND fromId= :userId) OR "
            + "(fromEntity='team' AND fromId IN (<teamIds>))) AND relation=8) OR "
            + "id in (SELECT toId FROM entity_relationship WHERE (fromEntity='user' AND fromId= :userId AND toEntity='THREAD' AND relation IN (1,2)))) ")
    int listCountThreadsByOwner(
        @Bind("userId") String userId,
        @BindList("teamIds") List<String> teamIds,
        @Define("condition") String condition);

    default List<String> listThreadsByEntityLink(
        FeedFilter filter, EntityLink entityLink, int limit, int relation, String userName, List<String> teamNames) {
      int filterRelation = -1;
      if (userName != null && filter.getFilterType() == FilterType.MENTIONS) {
        filterRelation = MENTIONED_IN.ordinal();
      }
      return listThreadsByEntityLink(
          entityLink.getFullyQualifiedFieldValue(),
          entityLink.getFullyQualifiedFieldType(),
          limit,
          relation,
          userName,
          teamNames,
          filterRelation,
          filter.getCondition());
    }

    @SqlQuery(
        "SELECT json FROM thread_entity <condition> "
            + "AND MD5(id) in (SELECT fromFQNHash FROM field_relationship WHERE "
            + "(:fqnPrefixHash IS NULL OR toFQNHash LIKE CONCAT(:fqnPrefixHash, '.%') OR toFQNHash=:fqnPrefixHash) AND fromType='THREAD' AND "
            + "(:toType IS NULL OR toType LIKE CONCAT(:toType, '.%') OR toType=:toType) AND relation= :relation) "
            + "AND (:userName IS NULL OR MD5(id) in (SELECT toFQNHash FROM field_relationship WHERE "
            + " ((fromType='user' AND fromFQNHash= :userName) OR"
            + " (fromType='team' AND fromFQNHash IN (<teamNames>))) AND toType='THREAD' AND relation= :filterRelation) )"
            + "ORDER BY createdAt DESC "
            + "LIMIT :limit")
    List<String> listThreadsByEntityLink(
        @BindFQN("fqnPrefixHash") String fqnPrefixHash,
        @Bind("toType") String toType,
        @Bind("limit") int limit,
        @Bind("relation") int relation,
        @BindFQN("userName") String userName,
        @BindList("teamNames") List<String> teamNames,
        @Bind("filterRelation") int filterRelation,
        @Define("condition") String condition);

    default int listCountThreadsByEntityLink(
        FeedFilter filter, EntityLink entityLink, int relation, String userName, List<String> teamNames) {
      int filterRelation = -1;
      if (userName != null && filter.getFilterType() == FilterType.MENTIONS) {
        filterRelation = MENTIONED_IN.ordinal();
      }
      return listCountThreadsByEntityLink(
          entityLink.getFullyQualifiedFieldValue(),
          entityLink.getFullyQualifiedFieldType(),
          relation,
          userName,
          teamNames,
          filterRelation,
          filter.getCondition(false));
    }

    @SqlQuery(
        "SELECT count(id) FROM thread_entity <condition> "
            + "AND MD5(id) in (SELECT fromFQNHash FROM field_relationship WHERE "
            + "(:fqnPrefixHash IS NULL OR toFQNHash LIKE CONCAT(:fqnPrefixHash, '.%') OR toFQNHash=:fqnPrefixHash) AND fromType='THREAD' AND "
            + "(:toType IS NULL OR toType LIKE CONCAT(:toType, '.%') OR toType=:toType) AND relation= :relation) "
            + "AND (:userName IS NULL OR id in (SELECT toFQNHash FROM field_relationship WHERE "
            + " ((fromType='user' AND fromFQNHash= :userName) OR"
            + " (fromType='team' AND fromFQNHash IN (<teamNames>))) AND toType='THREAD' AND relation= :filterRelation) )")
    int listCountThreadsByEntityLink(
        @BindFQN("fqnPrefixHash") String fqnPrefixHash,
        @Bind("toType") String toType,
        @Bind("relation") int relation,
        @Bind("userName") String userName,
        @BindList("teamNames") List<String> teamNames,
        @Bind("filterRelation") int filterRelation,
        @Define("condition") String condition);

    @ConnectionAwareSqlUpdate(value = "UPDATE thread_entity SET json = :json where id = :id", connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value = "UPDATE thread_entity SET json = (:json :: jsonb) where id = :id",
        connectionType = POSTGRES)
    void update(@Bind("id") String id, @Bind("json") String json);

    @SqlQuery(
        "SELECT entityLink, COUNT(id) count FROM field_relationship fr INNER JOIN thread_entity te ON fr.fromFQNHash=MD5(te.id) "
            + "WHERE (:fqnPrefixHash IS NULL OR fr.toFQNHash LIKE CONCAT(:fqnPrefixHash, '.%') OR fr.toFQNHash=:fqnPrefixHash) AND "
            + "(:toType IS NULL OR fr.toType like concat(:toType, '.%') OR fr.toType=:toType) AND fr.fromType = :fromType "
            + "AND fr.relation = :relation AND te.resolved= :isResolved AND (:status IS NULL OR te.taskStatus = :status) "
            + "AND (:type IS NULL OR te.type = :type) "
            + "GROUP BY entityLink")
    @RegisterRowMapper(CountFieldMapper.class)
    List<List<String>> listCountByEntityLink(
        @BindFQN("fqnPrefixHash") String fqnPrefixHash,
        @Bind("fromType") String fromType,
        @Bind("toType") String toType,
        @Bind("relation") int relation,
        @Bind("type") ThreadType type,
        @Bind("status") TaskStatus status,
        @Bind("isResolved") boolean isResolved);

    @SqlQuery(
        "SELECT entityLink, COUNT(id) count FROM thread_entity <condition> AND "
            + "(entityId in (SELECT toId FROM entity_relationship WHERE "
            + "((fromEntity='user' AND fromId= :userId) OR "
            + "(fromEntity='team' AND fromId IN (<teamIds>))) AND relation=8) OR "
            + "id in (SELECT toId FROM entity_relationship WHERE (fromEntity='user' AND fromId= :userId AND toEntity='THREAD' AND relation IN (1,2)))) "
            + "GROUP BY entityLink")
    @RegisterRowMapper(CountFieldMapper.class)
    List<List<String>> listCountByOwner(
        @Bind("userId") String userId,
        @BindList("teamIds") List<String> teamIds,
        @Define("condition") String condition);

    @SqlQuery(
        "SELECT json FROM thread_entity <condition> AND "
            + "entityId in ("
            + "SELECT toId FROM entity_relationship WHERE "
            + "((fromEntity='user' AND fromId= :userId) OR "
            + "(fromEntity='team' AND fromId IN (<teamIds>))) AND relation= :relation) "
            + "ORDER BY createdAt DESC "
            + "LIMIT :limit")
    List<String> listThreadsByFollows(
        @Bind("userId") String userId,
        @BindList("teamIds") List<String> teamIds,
        @Bind("limit") int limit,
        @Bind("relation") int relation,
        @Define("condition") String condition);

    @SqlQuery(
        "SELECT count(id) FROM thread_entity <condition> AND "
            + "entityId in ("
            + "SELECT toId FROM entity_relationship WHERE "
            + "((fromEntity='user' AND fromId= :userId) OR "
            + "(fromEntity='team' AND fromId IN (<teamIds>))) AND relation= :relation)")
    int listCountThreadsByFollows(
        @Bind("userId") String userId,
        @BindList("teamIds") List<String> teamIds,
        @Bind("relation") int relation,
        @Define("condition") String condition);

    @SqlQuery(
        "SELECT json FROM thread_entity <condition> AND "
            + "MD5(id) in ("
            + "SELECT toFQNHash FROM field_relationship WHERE "
            + "((fromType='user' AND fromFQNHash= :userName) OR "
            + "(fromType='team' AND fromFQNHash IN (<teamNames>)))  AND toType='THREAD' AND relation= :relation) "
            + "ORDER BY createdAt DESC "
            + "LIMIT :limit")
    List<String> listThreadsByMentions(
        @Bind("userName") String userName,
        @BindList("teamNames") List<String> teamNames,
        @Bind("limit") int limit,
        @Bind("relation") int relation,
        @Define("condition") String condition);

    @SqlQuery(
        "SELECT count(id) FROM thread_entity <condition> AND "
            + "MD5(id) in ("
            + "SELECT toFQNHash FROM field_relationship WHERE "
            + "((fromType='user' AND fromFQNHash= :userName) OR "
            + "(fromType='team' AND fromFQNHash IN (<teamNames>)))  AND toType='THREAD' AND relation= :relation) ")
    int listCountThreadsByMentions(
        @Bind("userName") String userName,
        @BindList("teamNames") List<String> teamNames,
        @Bind("relation") int relation,
        @Define("condition") String condition);

    @SqlQuery("select id from thread_entity where entityId = :entityId")
    List<String> findByEntityId(@Bind("entityId") String entityId);

    class CountFieldMapper implements RowMapper<List<String>> {
      @Override
      public List<String> map(ResultSet rs, StatementContext ctx) throws SQLException {
        return Arrays.asList(rs.getString("entityLink"), rs.getString("count"));
      }
    }
  }

  interface FieldRelationshipDAO {
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT IGNORE INTO field_relationship(fromFQNHash, toFQNHash, fromFQN, toFQN, fromType, toType, relation, json) "
                + "VALUES (:fromFQNHash, :toFQNHash, :fromFQN, :toFQN, :fromType, :toType, :relation, :json)",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO field_relationship(fromFQNHash, toFQNHash, fromFQN, toFQN, fromType, toType, relation, json) "
                + "VALUES (:fromFQNHash, :toFQNHash, :fromFQN, :toFQN, :fromType, :toType, :relation, (:json :: jsonb)) "
                + "ON CONFLICT (fromFQNHash, toFQNHash, relation) DO NOTHING",
        connectionType = POSTGRES)
    void insert(
        @BindFQN("fromFQNHash") String fromFQNHash,
        @BindFQN("toFQNHash") String toFQNHash,
        @Bind("fromFQN") String fromFQN,
        @Bind("toFQN") String toFQN,
        @Bind("fromType") String fromType,
        @Bind("toType") String toType,
        @Bind("relation") int relation,
        @Bind("json") String json);

    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO field_relationship(fromFQNHash, toFQNHash, fromFQN, toFQN, fromType, toType, relation, jsonSchema, json) "
                + "VALUES (:fromFQNHash, :toFQNHash, :fromFQN, :toFQN, :fromType, :toType, :relation, :jsonSchema, :json) "
                + "ON DUPLICATE KEY UPDATE json = :json",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO field_relationship(fromFQNHash, toFQNHash, fromFQN, toFQN, fromType, toType, relation, jsonSchema, json) "
                + "VALUES (:fromFQNHash, :toFQNHash, :fromFQN, :toFQN, :fromType, :toType, :relation, :jsonSchema, (:json :: jsonb)) "
                + "ON CONFLICT (fromFQNHash, toFQNHash, relation) DO UPDATE SET json = EXCLUDED.json",
        connectionType = POSTGRES)
    void upsert(
        @BindFQN("fromFQNHash") String fromFQNHash,
        @BindFQN("toFQNHash") String toFQNHash,
        @Bind("fromFQN") String fromFQN,
        @Bind("toFQN") String toFQN,
        @Bind("fromType") String fromType,
        @Bind("toType") String toType,
        @Bind("relation") int relation,
        @Bind("jsonSchema") String jsonSchema,
        @Bind("json") String json);

    @SqlQuery(
        "SELECT json FROM field_relationship WHERE "
            + "fromFQNHash = :fromFQNHash AND toFQNHash = :toFQNHash AND fromType = :fromType "
            + "AND toType = :toType AND relation = :relation")
    String find(
        @BindFQN("fromFQNHash") String fromFQNHash,
        @BindFQN("toFQNHash") String toFQNHash,
        @Bind("fromType") String fromType,
        @Bind("toType") String toType,
        @Bind("relation") int relation);

    @SqlQuery(
        "SELECT fromFQN, toFQN, json FROM field_relationship WHERE "
            + "fromFQNHash LIKE CONCAT(:fqnPrefixHash, '%') AND fromType = :fromType AND toType = :toType "
            + "AND relation = :relation")
    @RegisterRowMapper(ToFieldMapper.class)
    List<Triple<String, String, String>> listToByPrefix(
        @BindFQN("fqnPrefixHash") String fqnPrefixHash,
        @Bind("fromType") String fromType,
        @Bind("toType") String toType,
        @Bind("relation") int relation);

    @Deprecated
    @ConnectionAwareSqlQuery(
        value = "SELECT count(DISTINCT fromFQN, toFQN) FROM field_relationship",
        connectionType = MYSQL)
    @ConnectionAwareSqlQuery(
        value = "SELECT COUNT(*) FROM ( SELECT DISTINCT fromFQN, toFQN FROM field_relationship) AS subquery",
        connectionType = POSTGRES)
    int listDistinctCount();

    @Deprecated
    @SqlQuery(
        "SELECT DISTINCT fromFQN, toFQN FROM field_relationship WHERE fromFQNHash = '' or fromFQNHash is null or toFQNHash = '' or toFQNHash is null LIMIT :limit")
    @RegisterRowMapper(FieldRelationShipMapper.class)
    List<Pair<String, String>> migrationListDistinctWithOffset(@Bind("limit") int limit);

    @SqlQuery(
        "SELECT fromFQN, toFQN, json FROM field_relationship WHERE "
            + "fromFQNHash = :fqnHash AND fromType = :type AND toType = :otherType AND relation = :relation "
            + "UNION "
            + "SELECT toFQN, fromFQN, json FROM field_relationship WHERE "
            + "toFQNHash = :fqnHash AND toType = :type AND fromType = :otherType AND relation = :relation")
    @RegisterRowMapper(ToFieldMapper.class)
    List<Triple<String, String, String>> listBidirectional(
        @BindFQN("fqnHash") String fqnHash,
        @Bind("type") String type,
        @Bind("otherType") String otherType,
        @Bind("relation") int relation);

    @SqlQuery(
        "SELECT fromFQN, toFQN, json FROM field_relationship WHERE "
            + "fromFQNHash LIKE CONCAT(:fqnPrefixHash, '%') AND fromType = :type AND toType = :otherType AND relation = :relation "
            + "UNION "
            + "SELECT toFQN, fromFQN, json FROM field_relationship WHERE "
            + "toFQNHash LIKE CONCAT(:fqnPrefixHash, '%') AND toType = :type AND fromType = :otherType AND relation = :relation")
    @RegisterRowMapper(ToFieldMapper.class)
    List<Triple<String, String, String>> listBidirectionalByPrefix(
        @BindFQN("fqnPrefixHash") String fqnPrefixHash,
        @Bind("type") String type,
        @Bind("otherType") String otherType,
        @Bind("relation") int relation);

    default void deleteAllByPrefix(String fqn) {
      String prefix = String.format("%s%s%%", FullyQualifiedName.buildHash(fqn), Entity.SEPARATOR);
      String condition = "WHERE (toFQNHash LIKE :prefix OR fromFQNHash LIKE :prefix)";
      Map<String, String> bindMap = new HashMap<>();
      bindMap.put("prefix", prefix);
      deleteAllByPrefixInternal(condition, bindMap);
    }

    @SqlUpdate("DELETE from field_relationship <cond>")
    void deleteAllByPrefixInternal(@Define("cond") String cond, @BindMap Map<String, String> bindings);

    @SqlUpdate(
        "DELETE from field_relationship WHERE fromFQNHash = :fromFQNHash AND toFQNHash = :toFQNHash AND fromType = :fromType "
            + "AND toType = :toType AND relation = :relation")
    void delete(
        @BindFQN("fromFQNHash") String fromFQNHash,
        @BindFQN("toFQNHash") String toFQNHash,
        @Bind("fromType") String fromType,
        @Bind("toType") String toType,
        @Bind("relation") int relation);

    class ToFieldMapper implements RowMapper<Triple<String, String, String>> {
      @Override
      public Triple<String, String, String> map(ResultSet rs, StatementContext ctx) throws SQLException {
        return Triple.of(rs.getString("fromFQN"), rs.getString("toFQN"), rs.getString("json"));
      }
    }

    class FieldRelationShipMapper implements RowMapper<Pair<String, String>> {
      @Override
      public Pair<String, String> map(ResultSet rs, StatementContext ctx) throws SQLException {
        return Pair.of(rs.getString("fromFQN"), rs.getString("toFQN"));
      }
    }

    @Getter
    @Setter
    class FieldRelationship {
      private String fromFQNHash;
      private String toFQNHash;
      private String fromFQN;
      private String toFQN;
      private String fromType;
      private String toType;
      private int relation;
      private String jsonSchema;
      private String json;
    }
  }

  interface BotDAO extends EntityDAO<Bot> {
    @Override
    default String getTableName() {
      return "bot_entity";
    }

    @Override
    default Class<Bot> getEntityClass() {
      return Bot.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }
  }

  interface EventSubscriptionDAO extends EntityDAO<EventSubscription> {
    @Override
    default String getTableName() {
      return "event_subscription_entity";
    }

    @Override
    default Class<EventSubscription> getEntityClass() {
      return EventSubscription.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }

    @SqlQuery("SELECT json FROM <table>")
    List<String> listAllEventsSubscriptions(@Define("table") String table);

    @Override
    default boolean supportsSoftDelete() {
      return false;
    }
  }

  interface ChartDAO extends EntityDAO<Chart> {
    @Override
    default String getTableName() {
      return "chart_entity";
    }

    @Override
    default Class<Chart> getEntityClass() {
      return Chart.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }

  interface MessagingServiceDAO extends EntityDAO<MessagingService> {
    @Override
    default String getTableName() {
      return "messaging_service_entity";
    }

    @Override
    default Class<MessagingService> getEntityClass() {
      return MessagingService.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }
  }

  interface MetricsDAO extends EntityDAO<Metrics> {
    @Override
    default String getTableName() {
      return "metric_entity";
    }

    @Override
    default Class<Metrics> getEntityClass() {
      return Metrics.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }

  interface MlModelDAO extends EntityDAO<MlModel> {
    @Override
    default String getTableName() {
      return "ml_model_entity";
    }

    @Override
    default Class<MlModel> getEntityClass() {
      return MlModel.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }

  interface GlossaryDAO extends EntityDAO<Glossary> {
    @Override
    default String getTableName() {
      return "glossary_entity";
    }

    @Override
    default Class<Glossary> getEntityClass() {
      return Glossary.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }
  }

  interface GlossaryTermDAO extends EntityDAO<GlossaryTerm> {
    @Override
    default String getTableName() {
      return "glossary_term_entity";
    }

    @Override
    default Class<GlossaryTerm> getEntityClass() {
      return GlossaryTerm.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }

  interface IngestionPipelineDAO extends EntityDAO<IngestionPipeline> {
    @Override
    default String getTableName() {
      return "ingestion_pipeline_entity";
    }

    @Override
    default Class<IngestionPipeline> getEntityClass() {
      return IngestionPipeline.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }

    @Override
    default int listCount(ListFilter filter) {
      String serviceType = filter.getQueryParam("serviceType");
      String service = filter.getQueryParam("service");
      String condition = "INNER JOIN entity_relationship ON ingestion_pipeline_entity.id = entity_relationship.toId";
      String pipelineTypeCondition;
      Map<String, Object> bindMap = new HashMap<>();
      if (!CommonUtil.nullOrEmpty(serviceType)) {
        if (filter.getQueryParam("pipelineType") != null) {
          pipelineTypeCondition = String.format(" and %s", filter.getPipelineTypeCondition(null));
          condition += pipelineTypeCondition;
        }
        condition =
            String.format(
                "%s WHERE entity_relationship.fromEntity = :serviceType and entity_relationship.relation = :relation and ingestion_pipeline_entity.fullyQualifiedName LIKE :service",
                condition);
        bindMap.put("relation", CONTAINS.ordinal());
        bindMap.put("service", service + ".%");
        bindMap.put("serviceType", serviceType);
        return listIngestionPipelineCount(condition, bindMap);
      }
      return EntityDAO.super.listCount(filter);
    }

    @Override
    default List<String> listAfter(ListFilter filter, int limit, String after) {
      String serviceType = filter.getQueryParam("serviceType");
      String service = filter.getQueryParam("service");
      String condition = "INNER JOIN entity_relationship ON ingestion_pipeline_entity.id = entity_relationship.toId";
      String pipelineTypeCondition;
      Map<String, Object> bindMap = new HashMap<>();
      if (!CommonUtil.nullOrEmpty(serviceType)) {
        if (filter.getQueryParam("pipelineType") != null) {
          pipelineTypeCondition = filter.getPipelineTypeCondition(null);
          condition =
              String.format(
                  "%s WHERE entity_relationship.fromEntity = :serviceType and entity_relationship.relation = :relation and ingestion_pipeline_entity.fullyQualifiedName LIKE :service and ingestion_pipeline_entity.fullyQualifiedName > :after and %s order by ingestion_pipeline_entity.fullyQualifiedName ASC LIMIT :limit",
                  condition, pipelineTypeCondition);
        } else {
          condition =
              String.format(
                  "%s WHERE entity_relationship.fromEntity = :serviceType and entity_relationship.relation = :relation and ingestion_pipeline_entity.fullyQualifiedName LIKE :service and ingestion_pipeline_entity.fullyQualifiedName > :after order by ingestion_pipeline_entity.fullyQualifiedName ASC LIMIT :limit",
                  condition);
        }
        bindMap.put("serviceType", serviceType);
        bindMap.put("service", service + ".%");
        bindMap.put("relation", CONTAINS.ordinal());
        bindMap.put("after", after);
        bindMap.put("limit", limit);
        return listAfterIngestionPipelineByserviceType(condition, bindMap);
      }
      return EntityDAO.super.listAfter(filter, limit, after);
    }

    @Override
    default List<String> listBefore(ListFilter filter, int limit, String before) {
      String service = filter.getQueryParam("service");
      String serviceType = filter.getQueryParam("serviceType");
      String condition = "INNER JOIN entity_relationship ON ingestion_pipeline_entity.id = entity_relationship.toId";
      String pipelineTypeCondition;
      Map<String, Object> bindMap = new HashMap<>();
      if (!CommonUtil.nullOrEmpty(serviceType)) {
        if (filter.getQueryParam("pipelineType") != null) {
          pipelineTypeCondition = filter.getPipelineTypeCondition(null);
          condition =
              String.format(
                  "%s WHERE entity_relationship.fromEntity = :serviceType and entity_relationship.relation = :relation and ingestion_pipeline_entity.fullyQualifiedName LIKE :service and ingestion_pipeline_entity.fullyQualifiedName < :before and %s order by ingestion_pipeline_entity.fullyQualifiedName DESC LIMIT :limit",
                  condition, pipelineTypeCondition);
        } else {
          condition =
              String.format(
                  "%s WHERE entity_relationship.fromEntity = :serviceType and entity_relationship.relation = :relation and ingestion_pipeline_entity.fullyQualifiedName LIKE :service and ingestion_pipeline_entity.fullyQualifiedName < :before order by ingestion_pipeline_entity.fullyQualifiedName DESC LIMIT :limit",
                  condition);
        }
        bindMap.put("serviceType", serviceType);
        bindMap.put("service", service + ".%");
        bindMap.put("relation", CONTAINS.ordinal());
        bindMap.put("before", before);
        bindMap.put("limit", limit);
        return listBeforeIngestionPipelineByserviceType(condition, bindMap);
      }
      return EntityDAO.super.listBefore(filter, limit, before);
    }

    @SqlQuery("SELECT ingestion_pipeline_entity.json FROM ingestion_pipeline_entity <cond>")
    List<String> listAfterIngestionPipelineByserviceType(
        @Define("cond") String cond, @BindMap Map<String, Object> bindings);

    @SqlQuery(
        "SELECT json FROM (SELECT ingestion_pipeline_entity.fullyQualifiedName, ingestion_pipeline_entity.json FROM ingestion_pipeline_entity <cond>) last_rows_subquery ORDER BY fullyQualifiedName")
    List<String> listBeforeIngestionPipelineByserviceType(
        @Define("cond") String cond, @BindMap Map<String, Object> bindings);

    @SqlQuery("SELECT count(*) FROM ingestion_pipeline_entity <cond> ")
    int listIngestionPipelineCount(@Define("cond") String cond, @BindMap Map<String, Object> bindings);
  }

  interface PipelineServiceDAO extends EntityDAO<PipelineService> {
    @Override
    default String getTableName() {
      return "pipeline_service_entity";
    }

    @Override
    default Class<PipelineService> getEntityClass() {
      return PipelineService.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }
  }

  interface MlModelServiceDAO extends EntityDAO<MlModelService> {
    @Override
    default String getTableName() {
      return "mlmodel_service_entity";
    }

    @Override
    default Class<MlModelService> getEntityClass() {
      return MlModelService.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }
  }

  interface PolicyDAO extends EntityDAO<Policy> {
    @Override
    default String getTableName() {
      return "policy_entity";
    }

    @Override
    default Class<Policy> getEntityClass() {
      return Policy.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }

  interface ReportDAO extends EntityDAO<Report> {
    @Override
    default String getTableName() {
      return "report_entity";
    }

    @Override
    default Class<Report> getEntityClass() {
      return Report.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }

  interface TableDAO extends EntityDAO<Table> {
    @Override
    default String getTableName() {
      return "table_entity";
    }

    @Override
    default Class<Table> getEntityClass() {
      return Table.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }

    @Override
    default int listCount(ListFilter filter) {
      String includeEmptyTestSuite = filter.getQueryParam("includeEmptyTestSuite");
      if (includeEmptyTestSuite != null && !Boolean.parseBoolean(includeEmptyTestSuite)) {
        String condition =
            String.format(
                "INNER JOIN entity_relationship er ON %s.id=er.fromId AND er.relation=%s AND er.toEntity='%s'",
                getTableName(), CONTAINS.ordinal(), Entity.TEST_SUITE);
        String mySqlCondition = condition;
        String postgresCondition = condition;

        mySqlCondition = String.format("%s %s", mySqlCondition, filter.getCondition(getTableName()));
        postgresCondition = String.format("%s %s", postgresCondition, filter.getCondition(getTableName()));
        return listCount(getTableName(), getNameColumn(), mySqlCondition, postgresCondition);
      }

      String condition = filter.getCondition(getTableName());
      return listCount(getTableName(), getNameColumn(), condition, condition);
    }

    @Override
    default List<String> listBefore(ListFilter filter, int limit, String before) {
      String includeEmptyTestSuite = filter.getQueryParam("includeEmptyTestSuite");
      if (includeEmptyTestSuite != null && !Boolean.parseBoolean(includeEmptyTestSuite)) {
        String condition =
            String.format(
                "INNER JOIN entity_relationship er ON %s.id=er.fromId AND er.relation=%s AND er.toEntity='%s'",
                getTableName(), CONTAINS.ordinal(), Entity.TEST_SUITE);
        String mySqlCondition = condition;
        String postgresCondition = condition;

        mySqlCondition = String.format("%s %s", mySqlCondition, filter.getCondition(getTableName()));
        postgresCondition = String.format("%s %s", postgresCondition, filter.getCondition(getTableName()));
        return listBefore(getTableName(), getNameColumn(), mySqlCondition, postgresCondition, limit, before);
      }
      String condition = filter.getCondition(getTableName());
      return listBefore(getTableName(), getNameColumn(), condition, condition, limit, before);
    }

    @Override
    default List<String> listAfter(ListFilter filter, int limit, String after) {
      String includeEmptyTestSuite = filter.getQueryParam("includeEmptyTestSuite");
      if (includeEmptyTestSuite != null && !Boolean.parseBoolean(includeEmptyTestSuite)) {
        String condition =
            String.format(
                "INNER JOIN entity_relationship er ON %s.id=er.fromId AND er.relation=%s AND er.toEntity='%s'",
                getTableName(), CONTAINS.ordinal(), Entity.TEST_SUITE);
        String mySqlCondition = condition;
        String postgresCondition = condition;

        mySqlCondition = String.format("%s %s", mySqlCondition, filter.getCondition(getTableName()));
        postgresCondition = String.format("%s %s", postgresCondition, filter.getCondition(getTableName()));
        return listAfter(getTableName(), getNameColumn(), mySqlCondition, postgresCondition, limit, after);
      }
      String condition = filter.getCondition(getTableName());
      return listAfter(getTableName(), getNameColumn(), condition, condition, limit, after);
    }
  }

  interface QueryDAO extends EntityDAO<Query> {
    @Override
    default String getTableName() {
      return "query_entity";
    }

    @Override
    default Class<Query> getEntityClass() {
      return Query.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }

    @Override
    default boolean supportsSoftDelete() {
      return false;
    }

    @Override
    default int listCount(ListFilter filter) {
      String entityId = filter.getQueryParam("entityId");
      String condition = "INNER JOIN entity_relationship ON query_entity.id = entity_relationship.toId";
      Map<String, Object> bindMap = new HashMap<>();
      if (!CommonUtil.nullOrEmpty(entityId)) {
        condition =
            String.format(
                "%s WHERE entity_relationship.fromId = :id and entity_relationship.relation = :relation and entity_relationship.toEntity = :toEntityType",
                condition);
        bindMap.put("id", entityId);
        bindMap.put("relation", MENTIONED_IN.ordinal());
        bindMap.put("toEntityType", QUERY);
        return listQueryCount(condition, bindMap);
      }
      return EntityDAO.super.listCount(filter);
    }

    @Override
    default List<String> listBefore(ListFilter filter, int limit, String before) {
      String entityId = filter.getQueryParam("entityId");
      String condition = "INNER JOIN entity_relationship ON query_entity.id = entity_relationship.toId";
      Map<String, Object> bindMap = new HashMap<>();
      if (!CommonUtil.nullOrEmpty(entityId)) {
        condition =
            String.format(
                "%s WHERE entity_relationship.fromId = :entityId and entity_relationship.relation = :relation and entity_relationship.toEntity = :toEntity and query_entity.name < :before order by query_entity.name DESC LIMIT :limit",
                condition);
        bindMap.put("entityId", entityId);
        bindMap.put("relation", MENTIONED_IN.ordinal());
        bindMap.put("toEntity", QUERY);
        bindMap.put("before", before);
        bindMap.put("limit", limit);
        return listBeforeQueriesByEntityId(condition, bindMap);
      }
      return EntityDAO.super.listBefore(filter, limit, before);
    }

    @Override
    default List<String> listAfter(ListFilter filter, int limit, String after) {
      String entityId = filter.getQueryParam("entityId");
      String condition = "INNER JOIN entity_relationship ON query_entity.id = entity_relationship.toId";
      Map<String, Object> bindMap = new HashMap<>();
      if (!CommonUtil.nullOrEmpty(entityId)) {
        condition =
            String.format(
                "%s WHERE entity_relationship.fromId = :entityId and entity_relationship.relation = :relation and entity_relationship.toEntity = :toEntity and query_entity.name > :after order by query_entity.name ASC LIMIT :limit",
                condition);

        bindMap.put("entityId", entityId);
        bindMap.put("relation", MENTIONED_IN.ordinal());
        bindMap.put("toEntity", QUERY);
        bindMap.put("after", after);
        bindMap.put("limit", limit);
        return listAfterQueriesByEntityId(condition, bindMap);
      }
      return EntityDAO.super.listAfter(filter, limit, after);
    }

    @SqlQuery("SELECT query_entity.json FROM query_entity <cond>")
    List<String> listAfterQueriesByEntityId(@Define("cond") String cond, @BindMap Map<String, Object> bindings);

    @SqlQuery(
        "SELECT json FROM (SELECT query_entity.name, query_entity.json FROM query_entity <cond>) last_rows_subquery ORDER BY name")
    List<String> listBeforeQueriesByEntityId(@Define("cond") String cond, @BindMap Map<String, Object> bindings);

    @SqlQuery("SELECT count(*) FROM query_entity <cond> ")
    int listQueryCount(@Define("cond") String cond, @BindMap Map<String, Object> bindings);
  }

  interface PipelineDAO extends EntityDAO<Pipeline> {
    @Override
    default String getTableName() {
      return "pipeline_entity";
    }

    @Override
    default Class<Pipeline> getEntityClass() {
      return Pipeline.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }

  interface ClassificationDAO extends EntityDAO<Classification> {
    @Override
    default String getTableName() {
      return "classification";
    }

    @Override
    default Class<Classification> getEntityClass() {
      return Classification.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }
  }

  interface TagDAO extends EntityDAO<Tag> {
    @Override
    default String getTableName() {
      return "tag";
    }

    @Override
    default Class<Tag> getEntityClass() {
      return Tag.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }

    @SqlUpdate("DELETE FROM tag where fqnHash LIKE CONCAT(:fqnHashPrefix, '.%')")
    void deleteTagsByPrefix(@Bind("fqnHashPrefix") String fqnHashPrefix);

    @Override
    default int listCount(ListFilter filter) {
      boolean disabled = Boolean.parseBoolean(filter.getQueryParam("classification.disabled"));
      String condition =
          String.format(
              "INNER JOIN entity_relationship er ON tag.id=er.toId AND er.relation=%s AND er.fromEntity='%s'  "
                  + "INNER JOIN classification c on er.fromId=c.id",
              CONTAINS.ordinal(), Entity.CLASSIFICATION);
      String mySqlCondition = condition;
      String postgresCondition = condition;

      if (disabled) {
        mySqlCondition =
            String.format(
                "%s AND (JSON_EXTRACT(c.json, '$.disabled') IS NULL OR JSON_EXTRACT(c.json, '$.disabled') = TRUE)",
                mySqlCondition);
        postgresCondition =
            String.format(
                "%s AND ((c.json#>'{disabled}') IS NULL OR ((c.json#>'{disabled}')::boolean)  = TRUE)",
                postgresCondition);
      } else {
        mySqlCondition =
            String.format(
                "%s AND (JSON_EXTRACT(c.json, '$.disabled') IS NULL OR JSON_EXTRACT(c.json, '$.disabled') = FALSE)",
                mySqlCondition);
        postgresCondition =
            String.format(
                "%s AND ((c.json#>'{disabled}') IS NULL OR ((c.json#>'{disabled}')::boolean)  = FALSE)",
                postgresCondition);
      }

      mySqlCondition = String.format("%s %s", mySqlCondition, filter.getCondition("tag"));
      postgresCondition = String.format("%s %s", postgresCondition, filter.getCondition("tag"));
      return listCount(getTableName(), getNameColumn(), mySqlCondition, postgresCondition);
    }

    @Override
    default List<String> listBefore(ListFilter filter, int limit, String before) {
      boolean disabled = Boolean.parseBoolean(filter.getQueryParam("classification.disabled"));
      String condition =
          String.format(
              "INNER JOIN entity_relationship er ON tag.id=er.toId AND er.relation=%s AND er.fromEntity='%s'  "
                  + "INNER JOIN classification c on er.fromId=c.id",
              CONTAINS.ordinal(), Entity.CLASSIFICATION);

      String mySqlCondition = condition;
      String postgresCondition = condition;

      if (disabled) {
        mySqlCondition =
            String.format(
                "%s AND (JSON_EXTRACT(c.json, '$.disabled') IS NULL OR JSON_EXTRACT(c.json, '$.disabled') = TRUE)",
                mySqlCondition);
        postgresCondition =
            String.format(
                "%s AND ((c.json#>'{disabled}') IS NULL OR ((c.json#>'{disabled}')::boolean) = TRUE)",
                postgresCondition);
      } else {
        mySqlCondition =
            String.format(
                "%s AND (JSON_EXTRACT(c.json, '$.disabled') IS NULL OR JSON_EXTRACT(c.json, '$.disabled') = FALSE)",
                mySqlCondition);
        postgresCondition =
            String.format(
                "%s AND ((c.json#>'{disabled}') IS NULL OR ((c.json#>'{disabled}')::boolean)  = FALSE)",
                postgresCondition);
      }

      mySqlCondition = String.format("%s %s", mySqlCondition, filter.getCondition("tag"));
      postgresCondition = String.format("%s %s", postgresCondition, filter.getCondition("tag"));

      return listBefore(getTableName(), getNameColumn(), mySqlCondition, postgresCondition, limit, before);
    }

    @Override
    default List<String> listAfter(ListFilter filter, int limit, String after) {
      boolean disabled = Boolean.parseBoolean(filter.getQueryParam("classification.disabled"));
      String condition =
          String.format(
              "INNER JOIN entity_relationship er ON tag.id=er.toId AND er.relation=%s AND er.fromEntity='%s'  "
                  + "INNER JOIN classification c on er.fromId=c.id",
              CONTAINS.ordinal(), Entity.CLASSIFICATION);

      String mySqlCondition = condition;
      String postgresCondition = condition;

      if (disabled) {
        mySqlCondition =
            String.format(
                "%s AND (JSON_EXTRACT(c.json, '$.disabled') IS NULL OR JSON_EXTRACT(c.json, '$.disabled') = TRUE)",
                mySqlCondition);
        postgresCondition =
            String.format(
                "%s AND ((c.json#>'{disabled}') IS NULL OR ((c.json#>'{disabled}')::boolean) = TRUE)",
                postgresCondition);
      } else {
        mySqlCondition =
            String.format(
                "%s AND (JSON_EXTRACT(c.json, '$.disabled') IS NULL OR JSON_EXTRACT(c.json, '$.disabled') = FALSE)",
                mySqlCondition);
        postgresCondition =
            String.format(
                "%s AND ((c.json#>'{disabled}') IS NULL OR ((c.json#>'{disabled}')::boolean)  = FALSE)",
                postgresCondition);
      }

      mySqlCondition = String.format("%s %s", mySqlCondition, filter.getCondition("tag"));
      postgresCondition = String.format("%s %s", postgresCondition, filter.getCondition("tag"));
      return listAfter(getTableName(), getNameColumn(), mySqlCondition, postgresCondition, limit, after);
    }
  }

  @RegisterRowMapper(TagLabelMapper.class)
  interface TagUsageDAO {
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT IGNORE INTO tag_usage (source, tagFQN, tagFQNHash, targetFQNHash, labelType, state) VALUES (:source, :tagFQN, :tagFQNHash, :targetFQNHash, :labelType, :state)",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO tag_usage (source, tagFQN, tagFQNHash, targetFQNHash, labelType, state) VALUES (:source, :tagFQN, :tagFQNHash, :targetFQNHash, :labelType, :state) ON CONFLICT (source, tagFQNHash, targetFQNHash) DO NOTHING",
        connectionType = POSTGRES)
    void applyTag(
        @Bind("source") int source,
        @Bind("tagFQN") String tagFQN,
        @BindFQN("tagFQNHash") String tagFQNHash,
        @BindFQN("targetFQNHash") String targetFQNHash,
        @Bind("labelType") int labelType,
        @Bind("state") int state);

    @SqlQuery("SELECT targetFQNHash FROM tag_usage WHERE source = :source AND tagFQNHash = :tagFQNHash")
    List<String> getTargetFQNs(@Bind("source") int source, @BindFQN("tagFQNHash") String tagFQNHash);

    default List<TagLabel> getTags(String targetFQN) {
      List<TagLabel> tags = getTagsInternal(targetFQN);
      tags.forEach(tagLabel -> tagLabel.setDescription(TagLabelUtil.getDescription(tagLabel)));
      return tags;
    }

    @SqlQuery(
        "SELECT source, tagFQN,  labelType, state FROM tag_usage WHERE targetFQNHash = :targetFQNHash ORDER BY tagFQN")
    List<TagLabel> getTagsInternal(@BindFQN("targetFQNHash") String targetFQNHash);

    @SqlQuery("SELECT * FROM tag_usage")
    @Deprecated(since = "Release 1.1")
    @RegisterRowMapper(TagLabelMapperMigration.class)
    List<TagLabelMigration> listAll();

    @SqlQuery(
        "SELECT COUNT(*) FROM tag_usage "
            + "WHERE (tagFQNHash LIKE CONCAT(:tagFqnHash, '.%') OR tagFQNHash = :tagFqnHash) "
            + "AND source = :source")
    int getTagCount(@Bind("source") int source, @BindFQN("tagFqnHash") String tagFqnHash);

    @SqlUpdate("DELETE FROM tag_usage where targetFQNHash = :targetFQNHash")
    void deleteTagsByTarget(@BindFQN("targetFQNHash") String targetFQNHash);

    @SqlUpdate("DELETE FROM tag_usage where tagFQNHash = :tagFQNHash AND source = :source")
    void deleteTagLabels(@Bind("source") int source, @BindFQN("tagFQNHash") String tagFQNHash);

    @SqlUpdate("DELETE FROM tag_usage where tagFQNHash = :tagFQNHash")
    void deleteTagLabelsByFqn(@BindFQN("tagFQNHash") String tagFQNHash);

    @SqlUpdate("DELETE FROM tag_usage where tagFQNHash LIKE CONCAT(:tagFQNHash, '.%') AND source = :source")
    void deleteTagLabelsByPrefix(@Bind("source") int source, @Bind("tagFQNHash") String tagFQNHash);

    @SqlUpdate(
        "DELETE FROM tag_usage where targetFQNHash = :targetFQNHash OR targetFQNHash LIKE CONCAT(:targetFQNHash, '.%')")
    void deleteTagLabelsByTargetPrefix(@BindFQN("targetFQNHash") String targetFQNHash);

    @Deprecated(since = "Release 1.1")
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO tag_usage (source, tagFQN, tagFQNHash, targetFQNHash, labelType, state, targetFQN)"
                + "VALUES (:source, :tagFQN, :tagFQNHash, :targetFQNHash, :labelType, :state, :targetFQN) "
                + "ON DUPLICATE KEY UPDATE tagFQNHash = :tagFQNHash, targetFQNHash = :targetFQNHash",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO tag_usage (source, tagFQN, tagFQNHash, targetFQNHash, labelType, state, targetFQN) "
                + "VALUES (:source, :tagFQN, :tagFQNHash, :targetFQNHash, :labelType, :state, :targetFQN) "
                + "ON CONFLICT (source, tagFQN, targetFQN) "
                + "DO UPDATE SET tagFQNHash = EXCLUDED.tagFQNHash, targetFQNHash = EXCLUDED.targetFQNHash",
        connectionType = POSTGRES)
    void upsertFQNHash(
        @Bind("source") int source,
        @Bind("tagFQN") String tagFQN,
        @Bind("tagFQNHash") String tagFQNHash,
        @Bind("targetFQNHash") String targetFQNHash,
        @Bind("labelType") int labelType,
        @Bind("state") int state,
        @Bind("targetFQN") String targetFQN);

    /** Update all the tagFQN starting with oldPrefix to start with newPrefix due to tag or glossary name change */
    default void updateTagPrefix(int source, String oldPrefix, String newPrefix) {
      String update =
          String.format(
              "UPDATE tag_usage SET tagFQN = REPLACE(tagFQN, '%s.', '%s.'), tagFQNHash = REPLACE(tagFQNHash, '%s.', '%s.') WHERE source = %s AND tagFQNHash LIKE '%s.%%'",
              escapeApostrophe(oldPrefix),
              escapeApostrophe(newPrefix),
              FullyQualifiedName.buildHash(oldPrefix),
              FullyQualifiedName.buildHash(newPrefix),
              source,
              FullyQualifiedName.buildHash(oldPrefix));
      updateTagPrefixInternal(update);
    }

    default void rename(int source, String oldFQN, String newFQN) {
      renameInternal(source, oldFQN, newFQN, newFQN); // First rename tagFQN from oldFQN to newFQN
      updateTagPrefix(source, oldFQN, newFQN); // Rename all the tagFQN prefixes starting with the oldFQN to newFQN
    }

    /** Rename the tagFQN */
    @SqlUpdate(
        "Update tag_usage set tagFQN = :newFQN, tagFQNHash = :newFQNHash WHERE source = :source AND tagFQNHash = :oldFQNHash")
    void renameInternal(
        @Bind("source") int source,
        @BindFQN("oldFQNHash") String oldFQNHash,
        @Bind("newFQN") String newFQN,
        @BindFQN("newFQNHash") String newFQNHash);

    @SqlUpdate("<update>")
    void updateTagPrefixInternal(@Define("update") String update);

    class TagLabelMapper implements RowMapper<TagLabel> {
      @Override
      public TagLabel map(ResultSet r, StatementContext ctx) throws SQLException {
        return new TagLabel()
            .withSource(TagLabel.TagSource.values()[r.getInt("source")])
            .withLabelType(TagLabel.LabelType.values()[r.getInt("labelType")])
            .withState(TagLabel.State.values()[r.getInt("state")])
            .withTagFQN(r.getString("tagFQN"));
      }
    }

    @Getter
    @Setter
    @Deprecated(since = "Release 1.1")
    class TagLabelMigration {
      public int source;
      public String tagFQN;
      public String targetFQN;
      public int labelType;
      public int state;
      private String tagFQNHash;
      public String targetFQNHash;
    }

    @Deprecated(since = "Release 1.1")
    class TagLabelMapperMigration implements RowMapper<TagLabelMigration> {
      @Override
      public TagLabelMigration map(ResultSet r, StatementContext ctx) throws SQLException {
        TagLabelMigration tagLabel = new TagLabelMigration();

        tagLabel.setSource(r.getInt("source"));
        tagLabel.setLabelType(r.getInt("labelType"));
        tagLabel.setState(r.getInt("state"));
        tagLabel.setTagFQN(r.getString("tagFQN"));
        // TODO : Ugly ,  but this is present is lower version and removed on higher version
        try {
          // This field is removed in latest
          tagLabel.setTargetFQN(r.getString("targetFQN"));
        } catch (Exception ex) {
          // Nothing to do
        }
        try {
          tagLabel.setTagFQNHash(r.getString("tagFQNHash"));
        } catch (Exception ex) {
          // Nothing to do
        }
        try {
          tagLabel.setTargetFQNHash(r.getString("targetFQNHash"));
        } catch (Exception ex) {
          // Nothing to do
        }
        return tagLabel;
      }
    }
  }

  interface RoleDAO extends EntityDAO<Role> {
    @Override
    default String getTableName() {
      return "role_entity";
    }

    @Override
    default Class<Role> getEntityClass() {
      return Role.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }
  }

  interface TeamDAO extends EntityDAO<Team> {
    @Override
    default String getTableName() {
      return "team_entity";
    }

    @Override
    default Class<Team> getEntityClass() {
      return Team.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }

    @Override
    default int listCount(ListFilter filter) {
      String parentTeam = filter.getQueryParam("parentTeam");
      String isJoinable = filter.getQueryParam("isJoinable");
      String condition = filter.getCondition();
      if (parentTeam != null) {
        // validate parent team
        Team team = findEntityByName(parentTeam, filter.getInclude());
        if (ORGANIZATION_NAME.equals(team.getName())) {
          // All the teams without parents should come under "organization" team
          condition =
              String.format(
                  "%s AND id NOT IN ( (SELECT '%s') UNION (SELECT toId FROM entity_relationship WHERE fromId!='%s' AND fromEntity='team' AND toEntity='team' AND relation=%d) )",
                  condition, team.getId(), team.getId(), Relationship.PARENT_OF.ordinal());
        } else {
          condition =
              String.format(
                  "%s AND id IN (SELECT toId FROM entity_relationship WHERE fromId='%s' AND fromEntity='team' AND toEntity='team' AND relation=%d)",
                  condition, team.getId(), Relationship.PARENT_OF.ordinal());
        }
      }
      String mySqlCondition = condition;
      String postgresCondition = condition;
      if (isJoinable != null) {
        mySqlCondition = String.format("%s AND JSON_EXTRACT(json, '$.isJoinable') = %s ", mySqlCondition, isJoinable);
        postgresCondition =
            String.format("%s AND ((json#>'{isJoinable}')::boolean)  = %s ", postgresCondition, isJoinable);
      }

      return listCount(getTableName(), getNameColumn(), mySqlCondition, postgresCondition);
    }

    @Override
    default List<String> listBefore(ListFilter filter, int limit, String before) {
      String parentTeam = filter.getQueryParam("parentTeam");
      String isJoinable = filter.getQueryParam("isJoinable");
      String condition = filter.getCondition();
      if (parentTeam != null) {
        // validate parent team
        Team team = findEntityByName(parentTeam);
        if (ORGANIZATION_NAME.equals(team.getName())) {
          // All the parentless teams should come under "organization" team
          condition =
              String.format(
                  "%s AND id NOT IN ( (SELECT '%s') UNION (SELECT toId FROM entity_relationship WHERE fromId!='%s' AND fromEntity='team' AND toEntity='team' AND relation=%d) )",
                  condition, team.getId(), team.getId(), Relationship.PARENT_OF.ordinal());
        } else {
          condition =
              String.format(
                  "%s AND id IN (SELECT toId FROM entity_relationship WHERE fromId='%s' AND fromEntity='team' AND toEntity='team' AND relation=%d)",
                  condition, team.getId(), Relationship.PARENT_OF.ordinal());
        }
      }
      String mySqlCondition = condition;
      String postgresCondition = condition;
      if (isJoinable != null) {
        mySqlCondition = String.format("%s AND JSON_EXTRACT(json, '$.isJoinable') = %s ", mySqlCondition, isJoinable);
        postgresCondition =
            String.format("%s AND ((json#>'{isJoinable}')::boolean)  = %s ", postgresCondition, isJoinable);
      }

      // Quoted name is stored in fullyQualifiedName column and not in the name column
      before = getNameColumn().equals("name") ? FullyQualifiedName.unquoteName(before) : before;
      return listBefore(getTableName(), getNameColumn(), mySqlCondition, postgresCondition, limit, before);
    }

    @Override
    default List<String> listAfter(ListFilter filter, int limit, String after) {
      String parentTeam = filter.getQueryParam("parentTeam");
      String isJoinable = filter.getQueryParam("isJoinable");
      String condition = filter.getCondition();
      if (parentTeam != null) {
        // validate parent team
        Team team = findEntityByName(parentTeam, filter.getInclude());
        if (ORGANIZATION_NAME.equals(team.getName())) {
          // All the parentless teams should come under "organization" team
          condition =
              String.format(
                  "%s AND id NOT IN ( (SELECT '%s') UNION (SELECT toId FROM entity_relationship WHERE fromId!='%s' AND fromEntity='team' AND toEntity='team' AND relation=%d) )",
                  condition, team.getId(), team.getId(), Relationship.PARENT_OF.ordinal());
        } else {
          condition =
              String.format(
                  "%s AND id IN (SELECT toId FROM entity_relationship WHERE fromId='%s' AND fromEntity='team' AND toEntity='team' AND relation=%d)",
                  condition, team.getId(), Relationship.PARENT_OF.ordinal());
        }
      }
      String mySqlCondition = condition;
      String postgresCondition = condition;
      if (isJoinable != null) {
        mySqlCondition = String.format("%s AND JSON_EXTRACT(json, '$.isJoinable') = %s ", mySqlCondition, isJoinable);
        postgresCondition =
            String.format("%s AND ((json#>'{isJoinable}')::boolean)  = %s ", postgresCondition, isJoinable);
      }

      // Quoted name is stored in fullyQualifiedName column and not in the name column
      after = getNameColumn().equals("name") ? FullyQualifiedName.unquoteName(after) : after;
      return listAfter(getTableName(), getNameColumn(), mySqlCondition, postgresCondition, limit, after);
    }

    default List<String> listTeamsUnderOrganization(String teamId) {
      return listTeamsUnderOrganization(teamId, Relationship.PARENT_OF.ordinal());
    }

    @SqlQuery(
        "SELECT te.id "
            + "FROM team_entity te "
            + "WHERE te.id NOT IN (SELECT :teamId) UNION "
            + "(SELECT toId FROM entity_relationship "
            + "WHERE fromId != :teamId AND fromEntity = 'team' AND relation = :relation AND toEntity = 'team')")
    List<String> listTeamsUnderOrganization(@Bind("teamId") String teamId, @Bind("relation") int relation);
  }

  interface TopicDAO extends EntityDAO<Topic> {
    @Override
    default String getTableName() {
      return "topic_entity";
    }

    @Override
    default Class<Topic> getEntityClass() {
      return Topic.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }

  @RegisterRowMapper(UsageDetailsMapper.class)
  interface UsageDAO {
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO entity_usage (usageDate, id, entityType, count1, count7, count30) "
                + "SELECT :date, :id, :entityType, :count1, "
                + "(:count1 + (SELECT COALESCE(SUM(count1), 0) FROM entity_usage WHERE id = :id AND usageDate >= :date - "
                + "INTERVAL 6 DAY)), "
                + "(:count1 + (SELECT COALESCE(SUM(count1), 0) FROM entity_usage WHERE id = :id AND usageDate >= :date - "
                + "INTERVAL 29 DAY))"
                + "ON DUPLICATE KEY UPDATE count7 = count7 - count1 + :count1, count30 = count30 - count1 + :count1, count1 = :count1",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO entity_usage (usageDate, id, entityType, count1, count7, count30) "
                + "SELECT (:date :: date), :id, :entityType, :count1, "
                + "(:count1 + (SELECT COALESCE(SUM(count1), 0) FROM entity_usage WHERE id = :id AND usageDate >= (:date :: date) - INTERVAL '6 days')), "
                + "(:count1 + (SELECT COALESCE(SUM(count1), 0) FROM entity_usage WHERE id = :id AND usageDate >= (:date :: date) - INTERVAL '29 days'))"
                + "ON CONFLICT (usageDate, id) DO UPDATE SET count7 = entity_usage.count7 - entity_usage.count1 + :count1,"
                + "count30 = entity_usage.count30 - entity_usage.count1 + :count1, count1 = :count1",
        connectionType = POSTGRES)
    void insertOrReplaceCount(
        @Bind("date") String date,
        @Bind("id") String id,
        @Bind("entityType") String entityType,
        @Bind("count1") int count1);

    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO entity_usage (usageDate, id, entityType, count1, count7, count30) "
                + "SELECT :date, :id, :entityType, :count1, "
                + "(:count1 + (SELECT COALESCE(SUM(count1), 0) FROM entity_usage WHERE id = :id AND usageDate >= :date - "
                + "INTERVAL 6 DAY)), "
                + "(:count1 + (SELECT COALESCE(SUM(count1), 0) FROM entity_usage WHERE id = :id AND usageDate >= :date - "
                + "INTERVAL 29 DAY)) "
                + "ON DUPLICATE KEY UPDATE count1 = count1 + :count1, count7 = count7 + :count1, count30 = count30 + :count1",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO entity_usage (usageDate, id, entityType, count1, count7, count30) "
                + "SELECT (:date :: date), :id, :entityType, :count1, "
                + "(:count1 + (SELECT COALESCE(SUM(count1), 0) FROM entity_usage WHERE id = :id AND usageDate >= (:date :: date) - INTERVAL '6 days')), "
                + "(:count1 + (SELECT COALESCE(SUM(count1), 0) FROM entity_usage WHERE id = :id AND usageDate >= (:date :: date) - INTERVAL '29 days')) "
                + "ON CONFLICT (usageDate, id) DO UPDATE SET count1 = entity_usage.count1 + :count1, count7 = entity_usage.count7 + :count1, count30 = entity_usage.count30 + :count1",
        connectionType = POSTGRES)
    void insertOrUpdateCount(
        @Bind("date") String date,
        @Bind("id") String id,
        @Bind("entityType") String entityType,
        @Bind("count1") int count1);

    @ConnectionAwareSqlQuery(
        value =
            "SELECT id, usageDate, entityType, count1, count7, count30, "
                + "percentile1, percentile7, percentile30 FROM entity_usage "
                + "WHERE id = :id AND usageDate >= :date - INTERVAL :days DAY AND usageDate <= :date ORDER BY usageDate DESC",
        connectionType = MYSQL)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT id, usageDate, entityType, count1, count7, count30, "
                + "percentile1, percentile7, percentile30 FROM entity_usage "
                + "WHERE id = :id AND usageDate >= (:date :: date) - make_interval(days => :days) AND usageDate <= (:date :: date) ORDER BY usageDate DESC",
        connectionType = POSTGRES)
    List<UsageDetails> getUsageById(@Bind("id") String id, @Bind("date") String date, @Bind("days") int days);

    /** Get latest usage record */
    @SqlQuery(
        "SELECT id, usageDate, entityType, count1, count7, count30, "
            + "percentile1, percentile7, percentile30 FROM entity_usage "
            + "WHERE usageDate IN (SELECT MAX(usageDate) FROM entity_usage WHERE id = :id) AND id = :id")
    UsageDetails getLatestUsage(@Bind("id") String id);

    @SqlUpdate("DELETE FROM entity_usage WHERE id = :id")
    void delete(@Bind("id") String id);

    /**
     * TODO: Not sure I get what the next comment means, but tests now use mysql 8 so maybe tests can be improved here
     * Note not using in following percentile computation PERCENT_RANK function as unit tests use mysql5.7, and it does
     * not have window function
     */
    @ConnectionAwareSqlUpdate(
        value =
            "UPDATE entity_usage u JOIN ( "
                + "SELECT u1.id, "
                + "(SELECT COUNT(*) FROM entity_usage as u2 WHERE u2.count1 <  u1.count1 AND u2.entityType = :entityType "
                + "AND u2.usageDate = :date) as p1, "
                + "(SELECT COUNT(*) FROM entity_usage as u3 WHERE u3.count7 <  u1.count7 AND u3.entityType = :entityType "
                + "AND u3.usageDate = :date) as p7, "
                + "(SELECT COUNT(*) FROM entity_usage as u4 WHERE u4.count30 <  u1.count30 AND u4.entityType = :entityType "
                + "AND u4.usageDate = :date) as p30, "
                + "(SELECT COUNT(*) FROM entity_usage WHERE entityType = :entityType AND usageDate = :date) as total "
                + "FROM entity_usage u1 WHERE u1.entityType = :entityType AND u1.usageDate = :date"
                + ") vals ON u.id = vals.id AND usageDate = :date "
                + "SET u.percentile1 = ROUND(100 * p1/total, 2), u.percentile7 = ROUND(p7 * 100/total, 2), u.percentile30 ="
                + " ROUND(p30*100/total, 2)",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "UPDATE entity_usage u "
                + "SET percentile1 = ROUND(100 * p1 / total, 2), percentile7 = ROUND(p7 * 100 / total, 2), percentile30 = ROUND(p30 * 100 / total, 2) "
                + "FROM ("
                + "   SELECT u1.id, "
                + "       (SELECT COUNT(*) FROM entity_usage as u2 WHERE u2.count1 < u1.count1 AND u2.entityType = :entityType AND u2.usageDate = (:date :: date)) as p1, "
                + "       (SELECT COUNT(*) FROM entity_usage as u3 WHERE u3.count7 < u1.count7 AND u3.entityType = :entityType AND u3.usageDate = (:date :: date)) as p7, "
                + "       (SELECT COUNT(*) FROM entity_usage as u4 WHERE u4.count30 < u1.count30 AND u4.entityType = :entityType AND u4.usageDate = (:date :: date)) as p30, "
                + "       (SELECT COUNT(*) FROM entity_usage WHERE entityType = :entityType AND usageDate = (:date :: date)"
                + "   ) as total FROM entity_usage u1 "
                + "   WHERE u1.entityType = :entityType AND u1.usageDate = (:date :: date)"
                + ") vals "
                + "WHERE u.id = vals.id AND usageDate = (:date :: date);",
        connectionType = POSTGRES)
    void computePercentile(@Bind("entityType") String entityType, @Bind("date") String date);

    class UsageDetailsMapper implements RowMapper<UsageDetails> {
      @Override
      public UsageDetails map(ResultSet r, StatementContext ctx) throws SQLException {
        UsageStats dailyStats =
            new UsageStats().withCount(r.getInt("count1")).withPercentileRank(r.getDouble("percentile1"));
        UsageStats weeklyStats =
            new UsageStats().withCount(r.getInt("count7")).withPercentileRank(r.getDouble("percentile7"));
        UsageStats monthlyStats =
            new UsageStats().withCount(r.getInt("count30")).withPercentileRank(r.getDouble("percentile30"));
        return new UsageDetails()
            .withDate(r.getString("usageDate"))
            .withDailyStats(dailyStats)
            .withWeeklyStats(weeklyStats)
            .withMonthlyStats(monthlyStats);
      }
    }
  }

  interface UserDAO extends EntityDAO<User> {
    @Override
    default String getTableName() {
      return "user_entity";
    }

    @Override
    default Class<User> getEntityClass() {
      return User.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }

    @Override
    default int listCount(ListFilter filter) {
      String team = EntityInterfaceUtil.quoteName(filter.getQueryParam("team"));
      String isBotStr = filter.getQueryParam("isBot");
      String isAdminStr = filter.getQueryParam("isAdmin");
      String mySqlCondition = filter.getCondition("ue");
      String postgresCondition = filter.getCondition("ue");
      if (isAdminStr != null) {
        boolean isAdmin = Boolean.parseBoolean(isAdminStr);
        if (isAdmin) {
          mySqlCondition = String.format("%s AND JSON_EXTRACT(ue.json, '$.isAdmin') = TRUE ", mySqlCondition);
          postgresCondition = String.format("%s AND ((ue.json#>'{isAdmin}')::boolean)  = TRUE ", postgresCondition);
        } else {
          mySqlCondition =
              String.format(
                  "%s AND (JSON_EXTRACT(ue.json, '$.isAdmin') IS NULL OR JSON_EXTRACT(ue.json, '$.isAdmin') = FALSE ) ",
                  mySqlCondition);
          postgresCondition =
              String.format(
                  "%s AND (ue.json#>'{isAdmin}' IS NULL OR ((ue.json#>'{isAdmin}')::boolean) = FALSE ) ",
                  postgresCondition);
        }
      }
      if (isBotStr != null) {
        boolean isBot = Boolean.parseBoolean(isBotStr);
        if (isBot) {
          mySqlCondition = String.format("%s AND JSON_EXTRACT(ue.json, '$.isBot') = TRUE ", mySqlCondition);
          postgresCondition = String.format("%s AND ((ue.json#>'{isBot}')::boolean) = TRUE ", postgresCondition);
        } else {
          mySqlCondition =
              String.format(
                  "%s AND (JSON_EXTRACT(ue.json, '$.isBot') IS NULL OR JSON_EXTRACT(ue.json, '$.isBot') = FALSE ) ",
                  mySqlCondition);
          postgresCondition =
              String.format(
                  "%s AND (ue.json#>'{isBot}' IS NULL OR ((ue.json#>'{isBot}')::boolean) = FALSE) ", postgresCondition);
        }
      }
      if (team == null && isAdminStr == null && isBotStr == null) {
        return EntityDAO.super.listCount(filter);
      }
      return listCount(
          getTableName(), getNameColumn(), mySqlCondition, postgresCondition, team, Relationship.HAS.ordinal());
    }

    @Override
    default List<String> listBefore(ListFilter filter, int limit, String before) {
      String team = EntityInterfaceUtil.quoteName(filter.getQueryParam("team"));
      String isBotStr = filter.getQueryParam("isBot");
      String isAdminStr = filter.getQueryParam("isAdmin");
      String mySqlCondition = filter.getCondition("ue");
      String postgresCondition = filter.getCondition("ue");
      if (isAdminStr != null) {
        boolean isAdmin = Boolean.parseBoolean(isAdminStr);
        if (isAdmin) {
          mySqlCondition = String.format("%s AND JSON_EXTRACT(ue.json, '$.isAdmin') = TRUE ", mySqlCondition);
          postgresCondition = String.format("%s AND ((ue.json#>'{isAdmin}')::boolean) = TRUE ", postgresCondition);
        } else {
          mySqlCondition =
              String.format(
                  "%s AND (JSON_EXTRACT(ue.json, '$.isAdmin') IS NULL OR JSON_EXTRACT(ue.json, '$.isAdmin') = FALSE ) ",
                  mySqlCondition);
          postgresCondition =
              String.format(
                  "%s AND (ue.json#>'{isAdmin}' IS NULL OR ((ue.json#>'{isAdmin}')::boolean) = FALSE ) ",
                  postgresCondition);
        }
      }
      if (isBotStr != null) {
        boolean isBot = Boolean.parseBoolean(isBotStr);
        if (isBot) {
          mySqlCondition = String.format("%s AND JSON_EXTRACT(ue.json, '$.isBot') = TRUE ", mySqlCondition);
          postgresCondition = String.format("%s AND ((ue.json#>'{isBot}')::boolean) = TRUE ", postgresCondition);
        } else {
          mySqlCondition =
              String.format(
                  "%s AND (JSON_EXTRACT(ue.json, '$.isBot') IS NULL OR JSON_EXTRACT(ue.json, '$.isBot') = FALSE ) ",
                  mySqlCondition);
          postgresCondition =
              String.format(
                  "%s AND (ue.json#>'{isBot}' IS NULL OR ((ue.json#>'{isBot}')::boolean) = FALSE) ", postgresCondition);
        }
      }
      if (team == null && isAdminStr == null && isBotStr == null) {
        return EntityDAO.super.listBefore(filter, limit, before);
      }
      return listBefore(
          getTableName(),
          getNameColumn(),
          mySqlCondition,
          postgresCondition,
          team,
          limit,
          before,
          Relationship.HAS.ordinal());
    }

    @Override
    default List<String> listAfter(ListFilter filter, int limit, String after) {
      String team = EntityInterfaceUtil.quoteName(filter.getQueryParam("team"));
      String isBotStr = filter.getQueryParam("isBot");
      String isAdminStr = filter.getQueryParam("isAdmin");
      String mySqlCondition = filter.getCondition("ue");
      String postgresCondition = filter.getCondition("ue");
      if (isAdminStr != null) {
        boolean isAdmin = Boolean.parseBoolean(isAdminStr);
        if (isAdmin) {
          mySqlCondition = String.format("%s AND JSON_EXTRACT(ue.json, '$.isAdmin') = TRUE ", mySqlCondition);
          postgresCondition = String.format("%s AND ((ue.json#>'{isAdmin}')::boolean) = TRUE ", postgresCondition);
        } else {
          mySqlCondition =
              String.format(
                  "%s AND (JSON_EXTRACT(ue.json, '$.isAdmin') IS NULL OR JSON_EXTRACT(ue.json, '$.isAdmin') = FALSE ) ",
                  mySqlCondition);
          postgresCondition =
              String.format(
                  "%s AND (ue.json#>'{isAdmin}' IS NULL OR ((ue.json#>'{isAdmin}')::boolean) = FALSE ) ",
                  postgresCondition);
        }
      }
      if (isBotStr != null) {
        boolean isBot = Boolean.parseBoolean(isBotStr);
        if (isBot) {
          mySqlCondition = String.format("%s AND JSON_EXTRACT(ue.json, '$.isBot') = TRUE ", mySqlCondition);
          postgresCondition = String.format("%s AND ((ue.json#>'{isBot}')::boolean) = TRUE ", postgresCondition);
        } else {
          mySqlCondition =
              String.format(
                  "%s AND (JSON_EXTRACT(ue.json, '$.isBot') IS NULL OR JSON_EXTRACT(ue.json, '$.isBot') = FALSE ) ",
                  mySqlCondition);
          postgresCondition =
              String.format(
                  "%s AND (ue.json#>'{isBot}' IS NULL OR ((ue.json#>'{isBot}')::boolean) = FALSE) ", postgresCondition);
        }
      }
      if (team == null && isAdminStr == null && isBotStr == null) {
        return EntityDAO.super.listAfter(filter, limit, after);
      }
      return listAfter(
          getTableName(),
          getNameColumn(),
          mySqlCondition,
          postgresCondition,
          team,
          limit,
          after,
          Relationship.HAS.ordinal());
    }

    @ConnectionAwareSqlQuery(
        value =
            "SELECT count(id) FROM ("
                + "SELECT ue.id "
                + "FROM user_entity ue "
                + "LEFT JOIN entity_relationship er on ue.id = er.toId "
                + "LEFT JOIN team_entity te on te.id = er.fromId and er.relation = :relation "
                + " <mysqlCond> "
                + " AND (:team IS NULL OR te.nameHash = :team) "
                + "GROUP BY ue.id) subquery",
        connectionType = MYSQL)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT count(id) FROM ("
                + "SELECT ue.id "
                + "FROM user_entity ue "
                + "LEFT JOIN entity_relationship er on ue.id = er.toId "
                + "LEFT JOIN team_entity te on te.id = er.fromId and er.relation = :relation "
                + " <postgresCond> "
                + " AND (:team IS NULL OR te.nameHash = :team) "
                + "GROUP BY ue.id) subquery",
        connectionType = POSTGRES)
    int listCount(
        @Define("table") String table,
        @Define("nameColumn") String nameColumn,
        @Define("mysqlCond") String mysqlCond,
        @Define("postgresCond") String postgresCond,
        @BindFQN("team") String team,
        @Bind("relation") int relation);

    @ConnectionAwareSqlQuery(
        value =
            "SELECT json FROM ("
                + "SELECT ue.<nameColumn>, ue.json "
                + "FROM user_entity ue "
                + "LEFT JOIN entity_relationship er on ue.id = er.toId "
                + "LEFT JOIN team_entity te on te.id = er.fromId and er.relation = :relation "
                + " <mysqlCond> "
                + "AND (:team IS NULL OR te.nameHash = :team) "
                + "AND ue.<nameColumn> < :before "
                + "GROUP BY ue.<nameColumn>, ue.json "
                + "ORDER BY ue.<nameColumn> DESC "
                + "LIMIT :limit"
                + ") last_rows_subquery ORDER BY <nameColumn>",
        connectionType = MYSQL)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT json FROM ("
                + "SELECT ue.<nameColumn>, ue.json "
                + "FROM user_entity ue "
                + "LEFT JOIN entity_relationship er on ue.id = er.toId "
                + "LEFT JOIN team_entity te on te.id = er.fromId and er.relation = :relation "
                + " <postgresCond> "
                + "AND (:team IS NULL OR te.nameHash = :team) "
                + "AND ue.<nameColumn> < :before "
                + "GROUP BY ue.<nameColumn>, ue.json "
                + "ORDER BY ue.<nameColumn> DESC "
                + "LIMIT :limit"
                + ") last_rows_subquery ORDER BY <nameColumn>",
        connectionType = POSTGRES)
    List<String> listBefore(
        @Define("table") String table,
        @Define("nameColumn") String nameColumn,
        @Define("mysqlCond") String mysqlCond,
        @Define("postgresCond") String postgresCond,
        @BindFQN("team") String team,
        @Bind("limit") int limit,
        @Bind("before") String before,
        @Bind("relation") int relation);

    @ConnectionAwareSqlQuery(
        value =
            "SELECT ue.json "
                + "FROM user_entity ue "
                + "LEFT JOIN entity_relationship er on ue.id = er.toId "
                + "LEFT JOIN team_entity te on te.id = er.fromId and er.relation = :relation "
                + " <mysqlCond> "
                + "AND (:team IS NULL OR te.nameHash = :team) "
                + "AND ue.<nameColumn> > :after "
                + "GROUP BY ue.<nameColumn>, ue.json "
                + "ORDER BY ue.<nameColumn> "
                + "LIMIT :limit",
        connectionType = MYSQL)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT ue.json "
                + "FROM user_entity ue "
                + "LEFT JOIN entity_relationship er on ue.id = er.toId "
                + "LEFT JOIN team_entity te on te.id = er.fromId and er.relation = :relation "
                + " <postgresCond> "
                + "AND (:team IS NULL OR te.nameHash = :team) "
                + "AND ue.<nameColumn> > :after "
                + "GROUP BY ue.<nameColumn>, ue.json "
                + "ORDER BY ue.<nameColumn> "
                + "LIMIT :limit",
        connectionType = POSTGRES)
    List<String> listAfter(
        @Define("table") String table,
        @Define("nameColumn") String nameColumn,
        @Define("mysqlCond") String mysqlCond,
        @Define("postgresCond") String postgresCond,
        @BindFQN("team") String team,
        @Bind("limit") int limit,
        @Bind("after") String after,
        @Bind("relation") int relation);

    @SqlQuery("SELECT COUNT(*) FROM user_entity WHERE LOWER(email) = LOWER(:email)")
    int checkEmailExists(@Bind("email") String email);

    @SqlQuery("SELECT json FROM user_entity WHERE LOWER(email) = LOWER(:email)")
    String findUserByEmail(@Bind("email") String email);

    @Override
    default User findEntityByName(String fqn, Include include) {
      return EntityDAO.super.findEntityByName(fqn.toLowerCase(), include);
    }
  }

  interface ChangeEventDAO {
    @ConnectionAwareSqlUpdate(value = "INSERT INTO change_event (json) VALUES (:json)", connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value = "INSERT INTO change_event (json) VALUES (:json :: jsonb)",
        connectionType = POSTGRES)
    void insert(@Bind("json") String json);

    @SqlUpdate("DELETE FROM change_event WHERE entityType = :entityType")
    void deleteAll(@Bind("entityType") String entityType);

    default List<String> list(String eventType, List<String> entityTypes, long timestamp) {
      if (CommonUtil.nullOrEmpty(entityTypes)) {
        return Collections.emptyList();
      }
      if (entityTypes.get(0).equals("*")) {
        return listWithoutEntityFilter(eventType, timestamp);
      }
      return listWithEntityFilter(eventType, entityTypes, timestamp);
    }

    @SqlQuery(
        "SELECT json FROM change_event WHERE "
            + "eventType = :eventType AND (entityType IN (<entityTypes>)) AND eventTime >= :timestamp "
            + "ORDER BY eventTime ASC")
    List<String> listWithEntityFilter(
        @Bind("eventType") String eventType,
        @BindList("entityTypes") List<String> entityTypes,
        @Bind("timestamp") long timestamp);

    @SqlQuery(
        "SELECT json FROM change_event WHERE "
            + "eventType = :eventType AND eventTime >= :timestamp "
            + "ORDER BY eventTime ASC")
    List<String> listWithoutEntityFilter(@Bind("eventType") String eventType, @Bind("timestamp") long timestamp);
  }

  interface TypeEntityDAO extends EntityDAO<Type> {
    @Override
    default String getTableName() {
      return "type_entity";
    }

    @Override
    default Class<Type> getEntityClass() {
      return Type.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }

    @Override
    default boolean supportsSoftDelete() {
      return false;
    }
  }

  interface TestDefinitionDAO extends EntityDAO<TestDefinition> {
    @Override
    default String getTableName() {
      return "test_definition";
    }

    @Override
    default Class<TestDefinition> getEntityClass() {
      return TestDefinition.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }

    @Override
    default List<String> listBefore(ListFilter filter, int limit, String before) {
      String entityType = filter.getQueryParam("entityType");
      String testPlatform = filter.getQueryParam("testPlatform");
      String supportedDataType = filter.getQueryParam("supportedDataType");
      String condition = filter.getCondition();

      if (entityType == null && testPlatform == null && supportedDataType == null) {
        return EntityDAO.super.listBefore(filter, limit, before);
      }

      StringBuilder mysqlCondition = new StringBuilder();
      StringBuilder psqlCondition = new StringBuilder();

      mysqlCondition.append(String.format("%s ", condition));
      psqlCondition.append(String.format("%s ", condition));

      if (testPlatform != null) {
        mysqlCondition.append(String.format("AND json_extract(json, '$.testPlatforms') LIKE '%%%s%%' ", testPlatform));
        psqlCondition.append(String.format("AND json->>'testPlatforms' LIKE '%%%s%%' ", testPlatform));
      }

      if (entityType != null) {
        mysqlCondition.append(String.format("AND entityType='%s' ", entityType));
        psqlCondition.append(String.format("AND entityType='%s' ", entityType));
      }

      if (supportedDataType != null) {
        mysqlCondition.append(String.format("AND supported_data_types LIKE '%%%s%%' ", supportedDataType));
        String psqlStr = String.format("AND supported_data_types @> '`%s`' ", supportedDataType);
        psqlCondition.append(psqlStr.replace('`', '"'));
      }

      return listBefore(
          getTableName(), getNameColumn(), mysqlCondition.toString(), psqlCondition.toString(), limit, before);
    }

    @Override
    default List<String> listAfter(ListFilter filter, int limit, String after) {
      String entityType = filter.getQueryParam("entityType");
      String testPlatform = filter.getQueryParam("testPlatform");
      String supportedDataType = filter.getQueryParam("supportedDataType");
      String condition = filter.getCondition();

      if (entityType == null && testPlatform == null && supportedDataType == null) {
        return EntityDAO.super.listAfter(filter, limit, after);
      }

      StringBuilder mysqlCondition = new StringBuilder();
      StringBuilder psqlCondition = new StringBuilder();

      mysqlCondition.append(String.format("%s ", condition));
      psqlCondition.append(String.format("%s ", condition));

      if (testPlatform != null) {
        mysqlCondition.append(String.format("AND json_extract(json, '$.testPlatforms') LIKE '%%%s%%' ", testPlatform));
        psqlCondition.append(String.format("AND json->>'testPlatforms' LIKE '%%%s%%' ", testPlatform));
      }

      if (entityType != null) {
        mysqlCondition.append(String.format("AND entityType='%s' ", entityType));
        psqlCondition.append(String.format("AND entityType='%s' ", entityType));
      }

      if (supportedDataType != null) {
        mysqlCondition.append(String.format("AND supported_data_types LIKE '%%%s%%' ", supportedDataType));
        String psqlStr = String.format("AND supported_data_types @> '`%s`' ", supportedDataType);
        psqlCondition.append(psqlStr.replace('`', '"'));
      }

      return listAfter(
          getTableName(), getNameColumn(), mysqlCondition.toString(), psqlCondition.toString(), limit, after);
    }

    @Override
    default int listCount(ListFilter filter) {
      String entityType = filter.getQueryParam("entityType");
      String testPlatform = filter.getQueryParam("testPlatform");
      String supportedDataType = filter.getQueryParam("supportedDataType");
      String condition = filter.getCondition();

      if (entityType == null && testPlatform == null && supportedDataType == null) {
        return EntityDAO.super.listCount(filter);
      }

      StringBuilder mysqlCondition = new StringBuilder();
      StringBuilder psqlCondition = new StringBuilder();

      mysqlCondition.append(String.format("%s ", condition));
      psqlCondition.append(String.format("%s ", condition));

      if (testPlatform != null) {
        mysqlCondition.append(String.format("AND json_extract(json, '$.testPlatforms') LIKE '%%%s%%' ", testPlatform));
        psqlCondition.append(String.format("AND json->>'testPlatforms' LIKE '%%%s%%' ", testPlatform));
      }

      if (entityType != null) {
        mysqlCondition.append(String.format("AND entityType='%s' ", entityType));
        psqlCondition.append(String.format("AND entityType='%s' ", entityType));
      }

      if (supportedDataType != null) {
        mysqlCondition.append(String.format("AND supported_data_types LIKE '%%%s%%' ", supportedDataType));
        String psqlStr = String.format("AND supported_data_types @> '`%s`' ", supportedDataType);
        psqlCondition.append(psqlStr.replace('`', '"'));
      }
      return listCount(getTableName(), getNameColumn(), mysqlCondition.toString(), psqlCondition.toString());
    }

    @ConnectionAwareSqlQuery(
        value =
            "SELECT json FROM ("
                + "SELECT <nameColumn>, json FROM <table> <mysqlCond> AND "
                + "<nameColumn> < :before "
                + "ORDER BY <nameColumn> DESC "
                + "LIMIT :limit"
                + ") last_rows_subquery ORDER BY <nameColumn>",
        connectionType = MYSQL)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT json FROM ("
                + "SELECT <nameColumn>, json FROM <table> <psqlCond> AND "
                + "<nameColumn> < :before "
                + "ORDER BY <nameColumn> DESC "
                + "LIMIT :limit"
                + ") last_rows_subquery ORDER BY <nameColumn>",
        connectionType = POSTGRES)
    List<String> listBefore(
        @Define("table") String table,
        @Define("nameColumn") String nameColumn,
        @Define("mysqlCond") String mysqlCond,
        @Define("psqlCond") String psqlCond,
        @Bind("limit") int limit,
        @Bind("before") String before);

    @ConnectionAwareSqlQuery(
        value =
            "SELECT json FROM <table> <mysqlCond> AND "
                + "<nameColumn> > :after "
                + "ORDER BY <nameColumn> "
                + "LIMIT :limit",
        connectionType = MYSQL)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT json FROM <table> <psqlCond> AND "
                + "<nameColumn> > :after "
                + "ORDER BY <nameColumn> "
                + "LIMIT :limit",
        connectionType = POSTGRES)
    List<String> listAfter(
        @Define("table") String table,
        @Define("nameColumn") String nameColumn,
        @Define("mysqlCond") String mysqlCond,
        @Define("psqlCond") String psqlCond,
        @Bind("limit") int limit,
        @Bind("after") String after);

    @ConnectionAwareSqlQuery(value = "SELECT count(*) FROM <table> <mysqlCond>", connectionType = MYSQL)
    @ConnectionAwareSqlQuery(value = "SELECT count(*) FROM <table> <psqlCond>", connectionType = POSTGRES)
    int listCount(
        @Define("table") String table,
        @Define("nameColumn") String nameColumn,
        @Define("mysqlCond") String mysqlCond,
        @Define("psqlCond") String psqlCond);
  }

  interface TestSuiteDAO extends EntityDAO<TestSuite> {
    @Override
    default String getTableName() {
      return "test_suite";
    }

    @Override
    default Class<TestSuite> getEntityClass() {
      return TestSuite.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }

  interface TestCaseDAO extends EntityDAO<TestCase> {
    @Override
    default String getTableName() {
      return "test_case";
    }

    @Override
    default Class<TestCase> getEntityClass() {
      return TestCase.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }

    default int countOfTestCases(List<UUID> testCaseIds) {
      return countOfTestCases(getTableName(), testCaseIds.stream().map(Object::toString).collect(Collectors.toList()));
    }

    @SqlQuery("SELECT count(*) FROM <table> WHERE id IN (<testCaseIds>)")
    int countOfTestCases(@Define("table") String table, @BindList("testCaseIds") List<String> testCaseIds);

    @SqlQuery(
        "with \n"
            + "list_test_case as (\n"
            + "\tselect tc.*\n"
            + "\tfrom test_case tc \n"
            + "\twhere tc.deleted is false\n"
            + "),\n"
            + "test_case_result as (\n"
            + "\tselect * \n"
            + "\tfrom entity_extension_time_series\n"
            + "\twhere jsonschema = 'testCaseResult'\n"
            + "),\n"
            + "test_case_result_latest_timestamp as (\n"
            + "\tselect distinct entityfqnhash as entityfqnhash, max(\"timestamp\") as max_timestamp\n"
            + "\tfrom test_case_result \n"
            + "\twhere entityfqnhash in (\n"
            + "\t\tselect fqnhash from list_test_case\n"
            + "\t)\n"
            + "\tgroup by entityfqnhash\n"
            + "),\n"
            + "test_case_result_latest as (\n"
            + "\tselect tc.*\n"
            + "\tfrom test_case_result tc\n"
            + "\tinner join test_case_result_latest_timestamp tct on tc.entityfqnhash = tct.entityfqnhash and tc.timestamp = tct.max_timestamp\n"
            + ")\n"
            + "select tcr.json\n"
            + "from list_test_case tc \n"
            + "left join test_case_result_latest tcr on tc.fqnhash = tcr.entityfqnhash")
    List<String> getAllTestCaseResult();

    @SqlQuery(
        "with \n"
            + "list_test_case as (\n"
            + "\tselect tc.*\n"
            + "\tfrom test_case tc \n"
            + "\tinner join entity_relationship er on tc.id = er.toid  \n"
            + "\twhere er.fromid = :testSuiteId and tc.deleted is false\n"
            + "),\n"
            + "test_case_result as (\n"
            + "\tselect * \n"
            + "\tfrom entity_extension_time_series\n"
            + "\twhere jsonschema = 'testCaseResult'\n"
            + "),\n"
            + "test_case_result_latest_timestamp as (\n"
            + "\tselect distinct entityfqnhash as entityfqnhash, max(\"timestamp\") as max_timestamp\n"
            + "\tfrom test_case_result \n"
            + "\twhere entityfqnhash in (\n"
            + "\t\tselect fqnhash from list_test_case\n"
            + "\t)\n"
            + "\tgroup by entityfqnhash\n"
            + "),\n"
            + "test_case_result_latest as (\n"
            + "\tselect tc.*\n"
            + "\tfrom test_case_result tc\n"
            + "\tinner join test_case_result_latest_timestamp tct on tc.entityfqnhash = tct.entityfqnhash and tc.timestamp = tct.max_timestamp\n"
            + ")\n"
            + "select tcr.json\n"
            + "from list_test_case tc \n"
            + "left join test_case_result_latest tcr on tc.fqnhash = tcr.entityfqnhash")
    List<String> getAllTestCaseResultFromTestSuite(@Bind("testSuiteId") String testSuiteId);
  }

  interface WebAnalyticEventDAO extends EntityDAO<WebAnalyticEvent> {
    @Override
    default String getTableName() {
      return "web_analytic_event";
    }

    @Override
    default Class<WebAnalyticEvent> getEntityClass() {
      return WebAnalyticEvent.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }

  interface DataInsightChartDAO extends EntityDAO<DataInsightChart> {
    @Override
    default String getTableName() {
      return "data_insight_chart";
    }

    @Override
    default Class<DataInsightChart> getEntityClass() {
      return DataInsightChart.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }

  interface EntityExtensionTimeSeriesDAO {
    enum OrderBy {
      ASC,
      DESC
    }

    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO entity_extension_time_series(entityFQNHash, extension, jsonSchema, json) "
                + "VALUES (:entityFQNHash, :extension, :jsonSchema, :json)",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT INTO entity_extension_time_series(entityFQNHash, extension, jsonSchema, json) "
                + "VALUES (:entityFQNHash, :extension, :jsonSchema, (:json :: jsonb))",
        connectionType = POSTGRES)
    void insert(
        @BindFQN("entityFQNHash") String entityFQNHash,
        @Bind("extension") String extension,
        @Bind("jsonSchema") String jsonSchema,
        @Bind("json") String json);

    @ConnectionAwareSqlUpdate(
        value =
            "UPDATE entity_extension_time_series set json = :json where entityFQNHash=:entityFQNHash and extension=:extension and timestamp=:timestamp",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "UPDATE entity_extension_time_series set json = (:json :: jsonb) where entityFQNHash=:entityFQNHash and extension=:extension and timestamp=:timestamp",
        connectionType = POSTGRES)
    void update(
        @BindFQN("entityFQNHash") String entityFQNHash,
        @Bind("extension") String extension,
        @Bind("json") String json,
        @Bind("timestamp") Long timestamp);

    @ConnectionAwareSqlUpdate(
        value =
            "UPDATE entity_extension_time_series set json = :json where entityFQNHash=:entityFQNHash and extension=:extension and timestamp=:timestamp and json -> '$.operation' = :operation",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "UPDATE entity_extension_time_series set json = (:json :: jsonb) where entityFQNHash=:entityFQNHash and extension=:extension and timestamp=:timestamp and json #>>'{operation}' = :operation",
        connectionType = POSTGRES)
    void updateExtensionByOperation(
        @BindFQN("entityFQNHash") String entityFQNHash,
        @Bind("extension") String extension,
        @Bind("json") String json,
        @Bind("timestamp") Long timestamp,
        @Bind("operation") String operation);

    @SqlQuery(
        "SELECT json FROM entity_extension_time_series WHERE entityFQNHash = :entityFQNHash AND extension = :extension")
    String getExtension(@BindFQN("entityFQNHash") String entityId, @Bind("extension") String extension);

    @SqlQuery("SELECT count(*) FROM entity_extension_time_series WHERE entityFQNHash = :entityFQNHash")
    int listCount(@BindFQN("entityFQNHash") String entityFQNHash);

    @SqlQuery("SELECT COUNT(DISTINCT entityFQN) FROM entity_extension_time_series")
    @Deprecated
    int listDistinctCount();

    @ConnectionAwareSqlQuery(
        value =
            "WITH data AS (SELECT ROW_NUMBER() OVER(ORDER BY timestamp ASC) AS row_num, json "
                + "FROM entity_extension_time_series WHERE entityFQNHash = :entityFQNHash) "
                + "SELECT row_num, json FROM data WHERE row_num > :after LIMIT :limit",
        connectionType = MYSQL)
    @ConnectionAwareSqlQuery(
        value =
            "WITH data AS (SELECT ROW_NUMBER() OVER(ORDER BY timestamp ASC) AS row_num, json "
                + "FROM entity_extension_time_series WHERE entityFQNHash = :entityFQNHash) "
                + "SELECT row_num, json FROM data WHERE row_num > (:after :: integer) LIMIT :limit",
        connectionType = POSTGRES)
    @RegisterRowMapper(ReportDataMapper.class)
    List<ReportDataRow> getAfterExtension(
        @BindFQN("entityFQNHash") String entityFQNHash, @Bind("limit") int limit, @Bind("after") String after);

    @SqlQuery(
        "SELECT json FROM entity_extension_time_series WHERE entityFQNHash = :entityFQNHash AND extension = :extension AND timestamp = :timestamp")
    String getExtensionAtTimestamp(
        @BindFQN("entityFQNHash") String entityFQNHash,
        @Bind("extension") String extension,
        @Bind("timestamp") long timestamp);

    @ConnectionAwareSqlQuery(
        value =
            "SELECT json FROM entity_extension_time_series WHERE entityFQNHash = :entityFQNHash AND extension = :extension AND timestamp = :timestamp AND json -> '$.operation' = :operation",
        connectionType = MYSQL)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT json FROM entity_extension_time_series WHERE entityFQNHash = :entityFQNHash AND extension = :extension AND timestamp = :timestamp AND json #>>'{operation}' = :operation",
        connectionType = POSTGRES)
    String getExtensionAtTimestampWithOperation(
        @BindFQN("entityFQNHash") String entityFQNHash,
        @Bind("extension") String extension,
        @Bind("timestamp") long timestamp,
        @Bind("operation") String operation);

    @SqlQuery(
        "SELECT json FROM entity_extension_time_series WHERE entityFQNHash = :entityFQNHash AND extension = :extension "
            + "ORDER BY timestamp DESC LIMIT 1")
    String getLatestExtension(@BindFQN("entityFQNHash") String entityFQNHash, @Bind("extension") String extension);

    @SqlQuery(
        "SELECT ranked.json FROM (SELECT json, ROW_NUMBER() OVER(PARTITION BY entityFQNHash ORDER BY timestamp DESC) AS row_num "
            + "FROM entity_extension_time_series WHERE entityFQNHash IN (<entityFQNHashes>) AND extension = :extension) ranked WHERE ranked.row_num = 1")
    List<String> getLatestExtensionByFQNs(
        @BindList("entityFQNHashes") List<String> entityFQNHashes, @Bind("extension") String extension);

    @SqlQuery(
        "SELECT json FROM entity_extension_time_series WHERE extension = :extension "
            + "ORDER BY timestamp DESC LIMIT 1")
    String getLatestByExtension(@Bind("extension") String extension);

    @SqlQuery("SELECT json FROM entity_extension_time_series WHERE extension = :extension " + "ORDER BY timestamp DESC")
    List<String> getAllByExtension(@Bind("extension") String extension);

    @RegisterRowMapper(ExtensionMapper.class)
    @SqlQuery(
        "SELECT extension, json FROM entity_extension WHERE id = :id AND extension "
            + "LIKE CONCAT (:extensionPrefix, '.%') "
            + "ORDER BY extension")
    List<ExtensionRecord> getExtensions(@Bind("id") String id, @Bind("extensionPrefix") String extensionPrefix);

    @SqlUpdate("DELETE FROM entity_extension_time_series WHERE entityFQNHash = :entityFQNHash")
    void deleteAll(@Bind("entityFQNHash") String entityFQNHash);

    @SqlUpdate(
        "DELETE FROM entity_extension_time_series WHERE entityFQNHash = :entityFQNHash AND extension = :extension")
    void delete(@BindFQN("entityFQNHash") String entityFQNHash, @Bind("extension") String extension);

    // This just saves the limit number of records, and remove all other with given extension
    @SqlUpdate(
        "DELETE FROM entity_extension_time_series WHERE extension = :extension AND entityFQNHash NOT IN(SELECT entityFQNHash FROM (select * from entity_extension_time_series WHERE extension = :extension ORDER BY timestamp DESC LIMIT :records) AS subquery)")
    void deleteLastRecords(@Bind("extension") String extension, @Bind("records") int noOfRecord);

    @SqlUpdate(
        "DELETE FROM entity_extension_time_series WHERE entityFQNHash = :entityFQNHash AND extension = :extension AND timestamp = :timestamp")
    void deleteAtTimestamp(
        @BindFQN("entityFQNHash") String entityFQNHash,
        @Bind("extension") String extension,
        @Bind("timestamp") Long timestamp);

    @SqlUpdate(
        "DELETE FROM entity_extension_time_series WHERE entityFQNHash = :entityFQNHash AND extension = :extension AND timestamp < :timestamp")
    void deleteBeforeTimestamp(
        @BindFQN("entityFQNHash") String entityFQNHash,
        @Bind("extension") String extension,
        @Bind("timestamp") Long timestamp);

    @SqlQuery(
        "SELECT json FROM entity_extension_time_series where entityFQNHash = :entityFQNHash and extension = :extension "
            + " AND timestamp >= :startTs and timestamp <= :endTs ORDER BY timestamp DESC")
    List<String> listBetweenTimestamps(
        @BindFQN("entityFQNHash") String entityFQNHash,
        @Bind("extension") String extension,
        @Bind("startTs") Long startTs,
        @Bind("endTs") long endTs);

    @SqlQuery(
        "SELECT json FROM entity_extension_time_series where entityFQNHash = :entityFQNHash and extension = :extension "
            + " AND timestamp >= :startTs and timestamp <= :endTs ORDER BY timestamp <orderBy>")
    List<String> listBetweenTimestampsByOrder(
        @BindFQN("entityFQNHash") String entityFQNHash,
        @Bind("extension") String extension,
        @Bind("startTs") Long startTs,
        @Bind("endTs") long endTs,
        @Define("orderBy") OrderBy orderBy);

    default void updateExtensionByKey(String key, String value, String entityFQN, String extension, String json) {
      String mysqlCond = String.format("AND JSON_UNQUOTE(JSON_EXTRACT(json, '$.%s')) = :value", key);
      String psqlCond = String.format("AND json->>'%s' = :value", key);
      updateExtensionByKeyInternal(value, entityFQN, extension, json, mysqlCond, psqlCond);
    }

    default String getExtensionByKey(String key, String value, String entityFQN, String extension) {
      String mysqlCond = String.format("AND JSON_UNQUOTE(JSON_EXTRACT(json, '$.%s')) = :value", key);
      String psqlCond = String.format("AND json->>'%s' = :value", key);
      return getExtensionByKeyInternal(value, entityFQN, extension, mysqlCond, psqlCond);
    }

    default String getLatestExtensionByKey(String key, String value, String entityFQN, String extension) {
      String mysqlCond = String.format("AND JSON_UNQUOTE(JSON_EXTRACT(json, '$.%s')) = :value", key);
      String psqlCond = String.format("AND json->>'%s' = :value", key);
      return getLatestExtensionByKeyInternal(value, entityFQN, extension, mysqlCond, psqlCond);
    }

    /*
     * Support updating data filtering by top-level keys in the JSON
     */
    @ConnectionAwareSqlUpdate(
        value =
            "UPDATE entity_extension_time_series SET json = :json "
                + "WHERE entityFQNHash = :entityFQNHash "
                + "AND extension = :extension "
                + "<mysqlCond>",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "UPDATE entity_extension_time_series SET json = (:json :: jsonb) "
                + "WHERE entityFQNHash = :entityFQNHash "
                + "AND extension = :extension "
                + "<psqlCond>",
        connectionType = POSTGRES)
    void updateExtensionByKeyInternal(
        @Bind("value") String value,
        @BindFQN("entityFQNHash") String entityFQNHash,
        @Bind("extension") String extension,
        @Bind("json") String json,
        @Define("mysqlCond") String mysqlCond,
        @Define("psqlCond") String psqlCond);

    /*
     * Support selecting data filtering by top-level keys in the JSON
     */
    @ConnectionAwareSqlQuery(
        value =
            "SELECT json from entity_extension_time_series "
                + "WHERE entityFQNHash = :entityFQNHash "
                + "AND extension = :extension "
                + "<mysqlCond>",
        connectionType = MYSQL)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT json from entity_extension_time_series "
                + "WHERE entityFQNHash = :entityFQNHash "
                + "AND extension = :extension "
                + "<psqlCond>",
        connectionType = POSTGRES)
    String getExtensionByKeyInternal(
        @Bind("value") String value,
        @BindFQN("entityFQNHash") String entityFQNHash,
        @Bind("extension") String extension,
        @Define("mysqlCond") String mysqlCond,
        @Define("psqlCond") String psqlCond);

    @ConnectionAwareSqlQuery(
        value =
            "SELECT json from entity_extension_time_series "
                + "WHERE entityFQNHash = :entityFQNHash "
                + "AND extension = :extension "
                + "<mysqlCond> "
                + "ORDER BY timestamp DESC LIMIT 1",
        connectionType = MYSQL)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT json from entity_extension_time_series "
                + "WHERE entityFQNHash = :entityFQNHash "
                + "AND extension = :extension "
                + "<psqlCond> "
                + "ORDER BY timestamp DESC LIMIT 1",
        connectionType = POSTGRES)
    String getLatestExtensionByKeyInternal(
        @Bind("value") String value,
        @BindFQN("entityFQNHash") String entityFQNHash,
        @Bind("extension") String extension,
        @Define("mysqlCond") String mysqlCond,
        @Define("psqlCond") String psqlCond);

    class ReportDataMapper implements RowMapper<ReportDataRow> {
      @Override
      public ReportDataRow map(ResultSet rs, StatementContext ctx) throws SQLException {
        String rowNumber = rs.getString("row_num");
        String json = rs.getString("json");
        ReportData reportData;
        reportData = JsonUtils.readValue(json, ReportData.class);
        return new ReportDataRow(rowNumber, reportData);
      }
    }

    @SqlQuery(
        "SELECT DISTINCT entityFQN FROM entity_extension_time_series WHERE entityFQNHash = '' or entityFQNHash is null LIMIT :limit")
    @Deprecated
    List<String> migrationListDistinctWithOffset(@Bind("limit") int limit);
  }

  class EntitiesCountRowMapper implements RowMapper<EntitiesCount> {
    @Override
    public EntitiesCount map(ResultSet rs, StatementContext ctx) throws SQLException {
      return new EntitiesCount()
          .withTableCount(rs.getInt("tableCount"))
          .withTopicCount(rs.getInt("topicCount"))
          .withDashboardCount(rs.getInt("dashboardCount"))
          .withPipelineCount(rs.getInt("pipelineCount"))
          .withMlmodelCount(rs.getInt("mlmodelCount"))
          .withServicesCount(rs.getInt("servicesCount"))
          .withUserCount(rs.getInt("userCount"))
          .withTeamCount(rs.getInt("teamCount"))
          .withTestSuiteCount(rs.getInt("testSuiteCount"))
          .withStorageContainerCount(rs.getInt("storageContainerCount"))
          .withGlossaryCount(rs.getInt("glossaryCount"))
          .withGlossaryTermCount(rs.getInt("glossaryTermCount"));
    }
  }

  class ServicesCountRowMapper implements RowMapper<ServicesCount> {
    @Override
    public ServicesCount map(ResultSet rs, StatementContext ctx) throws SQLException {
      return new ServicesCount()
          .withDatabaseServiceCount(rs.getInt("databaseServiceCount"))
          .withMessagingServiceCount(rs.getInt("messagingServiceCount"))
          .withDashboardServiceCount(rs.getInt("dashboardServiceCount"))
          .withPipelineServiceCount(rs.getInt("pipelineServiceCount"))
          .withMlModelServiceCount(rs.getInt("mlModelServiceCount"))
          .withStorageServiceCount(rs.getInt("storageServiceCount"));
    }
  }

  interface SystemDAO {
    @ConnectionAwareSqlQuery(
        value =
            "SELECT (SELECT COUNT(*) FROM table_entity <cond>) as tableCount, "
                + "(SELECT COUNT(*) FROM topic_entity <cond>) as topicCount, "
                + "(SELECT COUNT(*) FROM dashboard_entity <cond>) as dashboardCount, "
                + "(SELECT COUNT(*) FROM pipeline_entity <cond>) as pipelineCount, "
                + "(SELECT COUNT(*) FROM ml_model_entity <cond>) as mlmodelCount, "
                + "(SELECT COUNT(*) FROM storage_container_entity <cond>) as storageContainerCount, "
                + "(SELECT COUNT(*) FROM glossary_entity <cond>) as glossaryCount, "
                + "(SELECT COUNT(*) FROM glossary_term_entity <cond>) as glossaryTermCount, "
                + "(SELECT (SELECT COUNT(*) FROM metadata_service_entity <cond>) + "
                + "(SELECT COUNT(*) FROM dbservice_entity <cond>)+"
                + "(SELECT COUNT(*) FROM messaging_service_entity <cond>)+ "
                + "(SELECT COUNT(*) FROM dashboard_service_entity <cond>)+ "
                + "(SELECT COUNT(*) FROM pipeline_service_entity <cond>)+ "
                + "(SELECT COUNT(*) FROM mlmodel_service_entity <cond>)+ "
                + "(SELECT COUNT(*) FROM storage_service_entity <cond>)) as servicesCount, "
                + "(SELECT COUNT(*) FROM user_entity <cond> AND (JSON_EXTRACT(json, '$.isBot') IS NULL OR JSON_EXTRACT(json, '$.isBot') = FALSE)) as userCount, "
                + "(SELECT COUNT(*) FROM team_entity <cond>) as teamCount, "
                + "(SELECT COUNT(*) FROM test_suite <cond>) as testSuiteCount",
        connectionType = MYSQL)
    @ConnectionAwareSqlQuery(
        value =
            "SELECT (SELECT COUNT(*) FROM table_entity <cond>) as tableCount, "
                + "(SELECT COUNT(*) FROM topic_entity <cond>) as topicCount, "
                + "(SELECT COUNT(*) FROM dashboard_entity <cond>) as dashboardCount, "
                + "(SELECT COUNT(*) FROM pipeline_entity <cond>) as pipelineCount, "
                + "(SELECT COUNT(*) FROM ml_model_entity <cond>) as mlmodelCount, "
                + "(SELECT COUNT(*) FROM storage_container_entity <cond>) as storageContainerCount, "
                + "(SELECT COUNT(*) FROM glossary_entity <cond>) as glossaryCount, "
                + "(SELECT COUNT(*) FROM glossary_term_entity <cond>) as glossaryTermCount, "
                + "(SELECT (SELECT COUNT(*) FROM metadata_service_entity <cond>) + "
                + "(SELECT COUNT(*) FROM dbservice_entity <cond>)+ "
                + "(SELECT COUNT(*) FROM messaging_service_entity <cond>)+ "
                + "(SELECT COUNT(*) FROM dashboard_service_entity <cond>)+ "
                + "(SELECT COUNT(*) FROM pipeline_service_entity <cond>)+ "
                + "(SELECT COUNT(*) FROM mlmodel_service_entity <cond>)+ "
                + "(SELECT COUNT(*) FROM storage_service_entity <cond>)) as servicesCount, "
                + "(SELECT COUNT(*) FROM user_entity <cond> AND (json#>'{isBot}' IS NULL OR ((json#>'{isBot}')::boolean) = FALSE)) as userCount, "
                + "(SELECT COUNT(*) FROM team_entity <cond>) as teamCount, "
                + "(SELECT COUNT(*) FROM test_suite <cond>) as testSuiteCount",
        connectionType = POSTGRES)
    @RegisterRowMapper(EntitiesCountRowMapper.class)
    EntitiesCount getAggregatedEntitiesCount(@Define("cond") String cond) throws StatementException;

    @SqlQuery(
        "SELECT (SELECT COUNT(*) FROM database_entity <cond>) as databaseServiceCount, "
            + "(SELECT COUNT(*) FROM messaging_service_entity <cond>) as messagingServiceCount, "
            + "(SELECT COUNT(*) FROM dashboard_service_entity <cond>) as dashboardServiceCount, "
            + "(SELECT COUNT(*) FROM pipeline_service_entity <cond>) as pipelineServiceCount, "
            + "(SELECT COUNT(*) FROM mlmodel_service_entity <cond>) as mlModelServiceCount, "
            + "(SELECT COUNT(*) FROM storage_service_entity <cond>) as storageServiceCount")
    @RegisterRowMapper(ServicesCountRowMapper.class)
    ServicesCount getAggregatedServicesCount(@Define("cond") String cond) throws StatementException;

    @SqlQuery("SELECT configType,json FROM openmetadata_settings")
    @RegisterRowMapper(SettingsRowMapper.class)
    List<Settings> getAllConfig() throws StatementException;

    @SqlQuery("SELECT configType, json FROM openmetadata_settings WHERE configType = :configType")
    @RegisterRowMapper(SettingsRowMapper.class)
    Settings getConfigWithKey(@Bind("configType") String configType) throws StatementException;

    @ConnectionAwareSqlUpdate(
        value =
            "INSERT into openmetadata_settings (configType, json)"
                + "VALUES (:configType, :json) ON DUPLICATE KEY UPDATE json = :json",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value =
            "INSERT into openmetadata_settings (configType, json)"
                + "VALUES (:configType, :json :: jsonb) ON CONFLICT (configType) DO UPDATE SET json = EXCLUDED.json",
        connectionType = POSTGRES)
    void insertSettings(@Bind("configType") String configType, @Bind("json") String json);

    @SqlUpdate(value = "DELETE from openmetadata_settings WHERE configType = :configType")
    void delete(@Bind("configType") String configType);
  }

  class SettingsRowMapper implements RowMapper<Settings> {
    @Override
    public Settings map(ResultSet rs, StatementContext ctx) throws SQLException {
      return getSettings(SettingsType.fromValue(rs.getString("configType")), rs.getString("json"));
    }

    public static Settings getSettings(SettingsType configType, String json) {
      Settings settings = new Settings();
      settings.setConfigType(configType);
      Object value;
      switch (configType) {
        case EMAIL_CONFIGURATION:
          value = JsonUtils.readValue(json, SmtpSettings.class);
          break;
        case CUSTOM_LOGO_CONFIGURATION:
          value = JsonUtils.readValue(json, LogoConfiguration.class);
          break;
        case SLACK_APP_CONFIGURATION:
          value = JsonUtils.readValue(json, String.class);
          break;
        case SLACK_BOT:
        case SLACK_INSTALLER:
          value = JsonUtils.readValue(json, new TypeReference<HashMap<String, Object>>() {});
          break;
        default:
          throw new IllegalArgumentException("Invalid Settings Type " + configType);
      }
      settings.setConfigValue(value);
      return settings;
    }
  }

  class TokenRowMapper implements RowMapper<TokenInterface> {
    @Override
    public TokenInterface map(ResultSet rs, StatementContext ctx) throws SQLException {
      return getToken(TokenType.fromValue(rs.getString("tokenType")), rs.getString("json"));
    }

    public static TokenInterface getToken(TokenType type, String json) {
      TokenInterface resp;
      switch (type) {
        case EMAIL_VERIFICATION:
          resp = JsonUtils.readValue(json, EmailVerificationToken.class);
          break;
        case PASSWORD_RESET:
          resp = JsonUtils.readValue(json, PasswordResetToken.class);
          break;
        case REFRESH_TOKEN:
          resp = JsonUtils.readValue(json, RefreshToken.class);
          break;
        case PERSONAL_ACCESS_TOKEN:
          resp = JsonUtils.readValue(json, PersonalAccessToken.class);
          break;
        default:
          throw new IllegalArgumentException("Invalid Token Type.");
      }
      return resp;
    }
  }

  interface TokenDAO {
    @SqlQuery("SELECT tokenType, json FROM user_tokens WHERE token = :token")
    @RegisterRowMapper(TokenRowMapper.class)
    TokenInterface findByToken(@Bind("token") String token) throws StatementException;

    @SqlQuery("SELECT tokenType, json FROM user_tokens WHERE userId = :userId AND tokenType = :tokenType ")
    @RegisterRowMapper(TokenRowMapper.class)
    List<TokenInterface> getAllUserTokenWithType(@Bind("userId") String userId, @Bind("tokenType") String tokenType)
        throws StatementException;

    @ConnectionAwareSqlUpdate(value = "INSERT INTO user_tokens (json) VALUES (:json)", connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value = "INSERT INTO user_tokens (json) VALUES (:json :: jsonb)",
        connectionType = POSTGRES)
    void insert(@Bind("json") String json);

    @ConnectionAwareSqlUpdate(
        value = "UPDATE user_tokens SET json = :json WHERE token = :token",
        connectionType = MYSQL)
    @ConnectionAwareSqlUpdate(
        value = "UPDATE user_tokens SET json = (:json :: jsonb) WHERE token = :token",
        connectionType = POSTGRES)
    void update(@Bind("token") String token, @Bind("json") String json);

    @SqlUpdate(value = "DELETE from user_tokens WHERE token = :token")
    void delete(@Bind("token") String token);

    @SqlUpdate(value = "DELETE from user_tokens WHERE token IN (<tokenIds>)")
    void deleteAll(@BindList("tokenIds") List<String> tokens);

    @SqlUpdate(value = "DELETE from user_tokens WHERE userid = :userid AND tokenType = :tokenType")
    void deleteTokenByUserAndType(@Bind("userid") String userid, @Bind("tokenType") String tokenType);
  }

  interface KpiDAO extends EntityDAO<Kpi> {
    @Override
    default String getTableName() {
      return "kpi_entity";
    }

    @Override
    default Class<Kpi> getEntityClass() {
      return Kpi.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }
  }

  interface WorkflowDAO extends EntityDAO<Workflow> {
    @Override
    default String getTableName() {
      return "automations_workflow";
    }

    @Override
    default Class<Workflow> getEntityClass() {
      return Workflow.class;
    }

    @Override
    default String getNameHashColumn() {
      return "nameHash";
    }

    @Override
    default List<String> listBefore(ListFilter filter, int limit, String before) {
      String workflowType = filter.getQueryParam("workflowType");
      String status = filter.getQueryParam("status");
      String condition = filter.getCondition();

      if (workflowType == null && status == null) {
        return EntityDAO.super.listBefore(filter, limit, before);
      }

      StringBuilder sqlCondition = new StringBuilder();
      sqlCondition.append(String.format("%s ", condition));

      if (workflowType != null) {
        sqlCondition.append(String.format("AND workflowType='%s' ", workflowType));
      }

      if (status != null) {
        sqlCondition.append(String.format("AND status='%s' ", status));
      }

      return listBefore(getTableName(), getNameHashColumn(), sqlCondition.toString(), limit, before);
    }

    @Override
    default List<String> listAfter(ListFilter filter, int limit, String after) {
      String workflowType = filter.getQueryParam("workflowType");
      String status = filter.getQueryParam("status");
      String condition = filter.getCondition();

      if (workflowType == null && status == null) {
        return EntityDAO.super.listAfter(filter, limit, after);
      }

      StringBuilder sqlCondition = new StringBuilder();
      sqlCondition.append(String.format("%s ", condition));

      if (workflowType != null) {
        sqlCondition.append(String.format("AND workflowType='%s' ", workflowType));
      }

      if (status != null) {
        sqlCondition.append(String.format("AND status='%s' ", status));
      }

      return listAfter(getTableName(), getNameHashColumn(), sqlCondition.toString(), limit, after);
    }

    @Override
    default int listCount(ListFilter filter) {
      String workflowType = filter.getQueryParam("workflowType");
      String status = filter.getQueryParam("status");
      String condition = filter.getCondition();

      if (workflowType == null && status == null) {
        return EntityDAO.super.listCount(filter);
      }

      StringBuilder sqlCondition = new StringBuilder();
      sqlCondition.append(String.format("%s ", condition));

      if (workflowType != null) {
        sqlCondition.append(String.format("AND workflowType='%s' ", workflowType));
      }

      if (status != null) {
        sqlCondition.append(String.format("AND status='%s' ", status));
      }

      return listCount(getTableName(), getNameHashColumn(), sqlCondition.toString());
    }

    @SqlQuery(
        value =
            "SELECT json FROM ("
                + "SELECT <nameColumn>, json FROM <table> <sqlCondition> AND "
                + "<nameColumn> < :before "
                + "ORDER BY <nameColumn> DESC "
                + "LIMIT :limit"
                + ") last_rows_subquery ORDER BY <nameColumn>")
    List<String> listBefore(
        @Define("table") String table,
        @Define("nameColumn") String nameColumn,
        @Define("sqlCondition") String sqlCondition,
        @Bind("limit") int limit,
        @Bind("before") String before);

    @SqlQuery(
        value =
            "SELECT json FROM <table> <sqlCondition> AND "
                + "<nameColumn> > :after "
                + "ORDER BY <nameColumn> "
                + "LIMIT :limit")
    List<String> listAfter(
        @Define("table") String table,
        @Define("nameColumn") String nameColumn,
        @Define("sqlCondition") String sqlCondition,
        @Bind("limit") int limit,
        @Bind("after") String after);

    @SqlQuery(value = "SELECT count(*) FROM <table> <sqlCondition>")
    int listCount(
        @Define("table") String table,
        @Define("nameColumn") String nameColumn,
        @Define("sqlCondition") String sqlCondition);
  }

  interface DataModelDAO extends EntityDAO<DashboardDataModel> {
    @Override
    default String getTableName() {
      return "dashboard_data_model_entity";
    }

    @Override
    default Class<DashboardDataModel> getEntityClass() {
      return DashboardDataModel.class;
    }

    @Override
    default String getNameHashColumn() {
      return "fqnHash";
    }
  }
}
