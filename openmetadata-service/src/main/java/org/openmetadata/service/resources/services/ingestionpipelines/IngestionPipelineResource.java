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

package org.openmetadata.service.resources.services.ingestionpipelines;

import static org.openmetadata.common.utils.CommonUtil.listOrEmpty;
import static org.openmetadata.service.Entity.FIELD_OWNER;
import static org.openmetadata.service.Entity.FIELD_PIPELINE_STATUS;
import static org.openmetadata.service.jdbi3.IngestionPipelineRepository.validateProfileSample;
import static org.openmetadata.service.resources.services.metadata.MetadataServiceResource.OPENMETADATA_SERVICE;

import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.json.JsonPatch;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.openmetadata.schema.ServiceEntityInterface;
import org.openmetadata.schema.api.data.RestoreEntity;
import org.openmetadata.schema.api.services.ingestionPipelines.CreateIngestionPipeline;
import org.openmetadata.schema.entity.services.DatabaseService;
import org.openmetadata.schema.entity.services.ingestionPipelines.IngestionPipeline;
import org.openmetadata.schema.entity.services.ingestionPipelines.PipelineServiceClientResponse;
import org.openmetadata.schema.entity.services.ingestionPipelines.PipelineStatus;
import org.openmetadata.schema.entity.services.ingestionPipelines.PipelineType;
import org.openmetadata.schema.metadataIngestion.MetadataToElasticSearchPipeline;
import org.openmetadata.schema.metadataIngestion.SourceConfig;
import org.openmetadata.schema.services.connections.metadata.OpenMetadataConnection;
import org.openmetadata.schema.type.EntityHistory;
import org.openmetadata.schema.type.EntityReference;
import org.openmetadata.schema.type.Include;
import org.openmetadata.schema.type.MetadataOperation;
import org.openmetadata.schema.type.ProviderType;
import org.openmetadata.sdk.PipelineServiceClient;
import org.openmetadata.service.Entity;
import org.openmetadata.service.OpenMetadataApplicationConfig;
import org.openmetadata.service.clients.pipeline.PipelineServiceClientFactory;
import org.openmetadata.service.jdbi3.*;
import org.openmetadata.service.resources.Collection;
import org.openmetadata.service.resources.EntityResource;
import org.openmetadata.service.secrets.SecretsManager;
import org.openmetadata.service.secrets.SecretsManagerFactory;
import org.openmetadata.service.secrets.masker.EntityMaskerFactory;
import org.openmetadata.service.security.AuthorizationException;
import org.openmetadata.service.security.Authorizer;
import org.openmetadata.service.security.policyevaluator.OperationContext;
import org.openmetadata.service.util.EntityUtil;
import org.openmetadata.service.util.EntityUtil.Fields;
import org.openmetadata.service.util.IngestionPipelineUtils;
import org.openmetadata.service.util.OpenMetadataConnectionBuilder;
import org.openmetadata.service.util.ResultList;

// TODO merge with workflows
@Slf4j
@Path("/v1/services/ingestionPipelines")
@Tag(
    name = "Ingestion Pipelines",
    description = "APIs related pipelines/workflows created by the system to ingest metadata.")
@Hidden
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Collection(name = "IngestionPipelines")
public class IngestionPipelineResource extends EntityResource<IngestionPipeline, IngestionPipelineRepository> {
  private static final String DEFAULT_INSIGHT_PIPELINE = "OpenMetadata_dataInsight";
  private static final String DEFAULT_REINDEX_PIPELINE = "OpenMetadata_elasticSearchReindex";
  public static final String COLLECTION_PATH = "v1/services/ingestionPipelines/";
  private PipelineServiceClient pipelineServiceClient;
  private OpenMetadataApplicationConfig openMetadataApplicationConfig;
  private final MetadataServiceRepository metadataServiceRepository;
  static final String FIELDS = FIELD_OWNER;
  private final DatabaseServiceRepository databaseServiceRepository;

  @Override
  public IngestionPipeline addHref(UriInfo uriInfo, IngestionPipeline ingestionPipeline) {
    Entity.withHref(uriInfo, ingestionPipeline.getOwner());
    Entity.withHref(uriInfo, ingestionPipeline.getService());
    return ingestionPipeline;
  }

  public IngestionPipelineResource(CollectionDAO dao, Authorizer authorizer) {
    super(IngestionPipeline.class, new IngestionPipelineRepository(dao), authorizer);
    this.metadataServiceRepository = new MetadataServiceRepository(dao);
    this.databaseServiceRepository = new DatabaseServiceRepository(dao);
  }

  @Override
  public void initialize(OpenMetadataApplicationConfig config) {
    this.openMetadataApplicationConfig = config;

    this.pipelineServiceClient =
        PipelineServiceClientFactory.createPipelineServiceClient(config.getPipelineServiceClientConfiguration());
    repository.setPipelineServiceClient(pipelineServiceClient);
    createIndexAndInsightPipeline(config);
  }

  private void createIndexAndInsightPipeline(OpenMetadataApplicationConfig config) {
    // Metadata Service is created only when ES config is present
    if (config.getElasticSearchConfiguration() != null) {
      try {
        EntityReference metadataService =
            this.metadataServiceRepository
                .getByName(null, OPENMETADATA_SERVICE, repository.getFields("id"))
                .getEntityReference();
        // Create Data Insights Pipeline
        CreateIngestionPipeline createPipelineRequest =
            new CreateIngestionPipeline()
                .withName(DEFAULT_INSIGHT_PIPELINE)
                .withDisplayName(DEFAULT_INSIGHT_PIPELINE)
                .withDescription("Data Insights Pipeline")
                .withPipelineType(PipelineType.DATA_INSIGHT)
                .withSourceConfig(
                    new SourceConfig()
                        .withConfig(
                            new MetadataToElasticSearchPipeline()
                                .withType(
                                    MetadataToElasticSearchPipeline.MetadataToESConfigType.METADATA_TO_ELASTIC_SEARCH)))
                .withAirflowConfig(IngestionPipelineUtils.getDefaultAirflowConfig())
                .withService(metadataService);
        // Get Pipeline
        IngestionPipeline dataInsightPipeline =
            getIngestionPipeline(createPipelineRequest, "system").withProvider(ProviderType.SYSTEM);
        repository.setFullyQualifiedName(dataInsightPipeline);
        repository.initializeEntity(dataInsightPipeline);

        // Create Reindex Pipeline
        createPipelineRequest
            .withName(DEFAULT_REINDEX_PIPELINE)
            .withDisplayName(DEFAULT_REINDEX_PIPELINE)
            .withDescription("Elastic Search Reindexing Pipeline")
            .withPipelineType(PipelineType.ELASTIC_SEARCH_REINDEX);
        // Get Pipeline
        IngestionPipeline elasticSearchPipeline =
            getIngestionPipeline(createPipelineRequest, "system").withProvider(ProviderType.SYSTEM);
        repository.setFullyQualifiedName(elasticSearchPipeline);
        repository.initializeEntity(elasticSearchPipeline);
      } catch (Exception ex) {
        LOG.error("[IngestionPipelineResource] Failed in Creating Reindex and Insight Pipeline", ex);
      }
    }
  }

  public static class IngestionPipelineList extends ResultList<IngestionPipeline> {
    /* Required for serde */
  }

  @GET
  @Valid
  @Operation(
      operationId = "listIngestionPipelines",
      summary = "List ingestion pipelines for metadata operations",
      description =
          "Get a list of airflow pipelines for metadata operations. Use `fields` parameter to get only necessary fields. "
              + " Use cursor-based pagination to limit the number "
              + "entries in the list using `limit` and `before` or `after` query params.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "List of ingestion workflows",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = IngestionPipeline.class)))
      })
  public ResultList<IngestionPipeline> list(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(
              description = "Fields requested in the returned resource",
              schema = @Schema(type = "string", example = FIELDS))
          @QueryParam("fields")
          String fieldsParam,
      @Parameter(
              description = "Filter Ingestion Pipelines by service fully qualified name",
              schema = @Schema(type = "string", example = "snowflakeWestCoast"))
          @QueryParam("service")
          String serviceParam,
      @Parameter(
              description = "Filter Ingestion Pipelines by test suite fully qualified name",
              schema = @Schema(type = "string", example = "service.db.schema.name.testSuite"))
          @QueryParam("testSuite")
          String testSuiteParam,
      @Parameter(
              description = "Filter Ingestion Pipelines by pipeline Type",
              schema = @Schema(type = "string", example = "elasticSearchReindex"))
          @QueryParam("pipelineType")
          String pipelineType,
      @Parameter(
              description = "Filter Ingestion Pipelines by service Type",
              schema = @Schema(type = "string", example = "messagingService"))
          @QueryParam("serviceType")
          String serviceType,
      @Parameter(description = "Limit the number ingestion returned. (1 to 1000000, " + "default = 10)")
          @DefaultValue("10")
          @Min(0)
          @Max(1000000)
          @QueryParam("limit")
          int limitParam,
      @Parameter(description = "Returns list of ingestion before this cursor", schema = @Schema(type = "string"))
          @QueryParam("before")
          String before,
      @Parameter(description = "Returns list of ingestion after this cursor", schema = @Schema(type = "string"))
          @QueryParam("after")
          String after,
      @Parameter(
              description = "Include all, deleted, or non-deleted entities.",
              schema = @Schema(implementation = Include.class))
          @QueryParam("include")
          @DefaultValue("non-deleted")
          Include include) {
    ListFilter filter =
        new ListFilter(include)
            .addQueryParam("service", serviceParam)
            .addQueryParam("pipelineType", pipelineType)
            .addQueryParam("serviceType", serviceType)
            .addQueryParam("testSuite", testSuiteParam);
    ResultList<IngestionPipeline> ingestionPipelines =
        super.listInternal(uriInfo, securityContext, fieldsParam, filter, limitParam, before, after);

    for (IngestionPipeline ingestionPipeline : listOrEmpty(ingestionPipelines.getData())) {
      if (fieldsParam != null && fieldsParam.contains(FIELD_PIPELINE_STATUS)) {
        ingestionPipeline.setPipelineStatuses(repository.getLatestPipelineStatus(ingestionPipeline));
      }
      decryptOrNullify(securityContext, ingestionPipeline, false);
    }
    return ingestionPipelines;
  }

  @GET
  @Path("/{id}/versions")
  @Operation(
      operationId = "listAllIngestionPipelineVersion",
      summary = "List ingestion workflow versions",
      description = "Get a list of all the versions of a ingestion pipeline identified by `Id`",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "List of IngestionPipeline versions",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = EntityHistory.class)))
      })
  public EntityHistory listVersions(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(description = "Id of the ingestion pipeline", schema = @Schema(type = "UUID")) @PathParam("id")
          UUID id) {
    return super.listVersionsInternal(securityContext, id);
  }

  @GET
  @Path("/{id}")
  @Operation(
      operationId = "getIngestionPipelineByID",
      summary = "Get an ingestion pipeline by Id",
      description = "Get an ingestion pipeline by `Id`.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The ingestion",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = IngestionPipeline.class))),
        @ApiResponse(responseCode = "404", description = "IngestionPipeline for instance {id} is not found")
      })
  public IngestionPipeline get(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(description = "Id of the ingestion pipeline", schema = @Schema(type = "UUID")) @PathParam("id")
          UUID id,
      @Parameter(
              description = "Fields requested in the returned resource",
              schema = @Schema(type = "string", example = FIELDS))
          @QueryParam("fields")
          String fieldsParam,
      @Parameter(
              description = "Include all, deleted, or non-deleted entities.",
              schema = @Schema(implementation = Include.class))
          @QueryParam("include")
          @DefaultValue("non-deleted")
          Include include) {
    IngestionPipeline ingestionPipeline = getInternal(uriInfo, securityContext, id, fieldsParam, include);
    if (fieldsParam != null && fieldsParam.contains(FIELD_PIPELINE_STATUS)) {
      ingestionPipeline.setPipelineStatuses(repository.getLatestPipelineStatus(ingestionPipeline));
    }
    decryptOrNullify(securityContext, ingestionPipeline, false);
    return ingestionPipeline;
  }

  @GET
  @Path("/{id}/versions/{version}")
  @Operation(
      operationId = "getSpecificIngestionPipelineVersion",
      summary = "Get a version of the ingestion pipeline",
      description = "Get a version of the ingestion pipeline by given `Id`",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "IngestionPipelines",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = IngestionPipeline.class))),
        @ApiResponse(
            responseCode = "404",
            description = "IngestionPipeline for instance {id} and version  " + "{version} is not found")
      })
  public IngestionPipeline getVersion(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(description = "Id of the ingestion pipeline", schema = @Schema(type = "UUID")) @PathParam("id")
          UUID id,
      @Parameter(
              description = "Ingestion version number in the form `major`.`minor`",
              schema = @Schema(type = "string", example = "0.1 or 1.1"))
          @PathParam("version")
          String version) {
    IngestionPipeline ingestionPipeline = super.getVersionInternal(securityContext, id, version);
    decryptOrNullify(securityContext, ingestionPipeline, false);
    return ingestionPipeline;
  }

  @GET
  @Path("/name/{fqn}")
  @Operation(
      operationId = "getSpecificIngestionPipelineByFQN",
      summary = "Get an ingestion pipeline by fully qualified name",
      description = "Get an ingestion by fully qualified name.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "IngestionPipeline",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = IngestionPipeline.class))),
        @ApiResponse(responseCode = "404", description = "Ingestion for instance {fqn} is not found")
      })
  public IngestionPipeline getByName(
      @Context UriInfo uriInfo,
      @Parameter(description = "Fully qualified name of the ingestion pipeline", schema = @Schema(type = "string"))
          @PathParam("fqn")
          String fqn,
      @Context SecurityContext securityContext,
      @Parameter(
              description = "Fields requested in the returned resource",
              schema = @Schema(type = "string", example = FIELDS))
          @QueryParam("fields")
          String fieldsParam,
      @Parameter(
              description = "Include all, deleted, or non-deleted entities.",
              schema = @Schema(implementation = Include.class))
          @QueryParam("include")
          @DefaultValue("non-deleted")
          Include include) {
    IngestionPipeline ingestionPipeline = getByNameInternal(uriInfo, securityContext, fqn, fieldsParam, include);
    if (fieldsParam != null && fieldsParam.contains(FIELD_PIPELINE_STATUS)) {
      ingestionPipeline.setPipelineStatuses(repository.getLatestPipelineStatus(ingestionPipeline));
    }
    decryptOrNullify(securityContext, ingestionPipeline, false);
    return ingestionPipeline;
  }

  @POST
  @Operation(
      operationId = "createIngestionPipeline",
      summary = "Create an ingestion pipeline",
      description = "Create a new ingestion pipeline.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The Ingestion Pipeline",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = IngestionPipeline.class))),
        @ApiResponse(responseCode = "400", description = "Bad request")
      })
  public Response create(
      @Context UriInfo uriInfo, @Context SecurityContext securityContext, @Valid CreateIngestionPipeline create) {
    //    boolean isExist = isExist(create);
    //    if (isExist) {
    //      return Response.status(CONFLICT)
    //          .type(MediaType.APPLICATION_JSON_TYPE)
    //          .entity(new ErrorMessage(CONFLICT.getStatusCode(), CatalogExceptionMessage.ENTITY_ALREADY_EXISTS))
    //          .build();
    //    }
    IngestionPipeline ingestionPipeline = getIngestionPipeline(create, securityContext.getUserPrincipal().getName());
    Response response = create(uriInfo, securityContext, ingestionPipeline);
    validateProfileSample(ingestionPipeline);
    decryptOrNullify(securityContext, (IngestionPipeline) response.getEntity(), false);
    return response;
  }

  private boolean isExist(CreateIngestionPipeline create) {
    boolean isExist = false;

    if (Entity.DATABASE_SERVICE.equals(create.getService().getType())) {
      DatabaseService databaseService =
          databaseServiceRepository.find(create.getService().getId(), Include.NON_DELETED);

      ListFilter filter =
          new ListFilter()
              .addQueryParam("pipelineType", create.getPipelineType().value())
              .addQueryParam("service", databaseService.getName());
      EntityUtil.Fields fields = EntityUtil.Fields.EMPTY_FIELDS;

      List<IngestionPipeline> pipelines = repository.listAll(fields, filter);
      isExist =
          pipelines.stream()
              .anyMatch(
                  ingestionPipeline ->
                      ingestionPipeline
                          .getAirflowConfig()
                          .getScheduleInterval()
                          .equals(create.getAirflowConfig().getScheduleInterval()));
    }
    return isExist;
  }

  @PATCH
  @Path("/{id}")
  @Operation(
      operationId = "patchIngestionPipeline",
      summary = "Update an ingestion pipeline",
      description = "Update an existing ingestion pipeline using JsonPatch.",
      externalDocs = @ExternalDocumentation(description = "JsonPatch RFC", url = "https://tools.ietf.org/html/rfc6902"))
  @Consumes(MediaType.APPLICATION_JSON_PATCH_JSON)
  public Response updateDescription(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(description = "Id of the ingestion pipeline", schema = @Schema(type = "UUID")) @PathParam("id")
          UUID id,
      @RequestBody(
              description = "JsonPatch with array of operations",
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_PATCH_JSON,
                      examples = {
                        @ExampleObject("[" + "{op:remove, path:/a}," + "{op:add, path: /b, value: val}" + "]")
                      }))
          JsonPatch patch) {
    Response response = patchInternal(uriInfo, securityContext, id, patch);
    decryptOrNullify(securityContext, (IngestionPipeline) response.getEntity(), false);
    return response;
  }

  private boolean isValidCron(String cronExpress) {
    String cronRegex =
        "^([0-5]?\\d|\\*)\\s+([01]?\\d|2[0-3]|\\*)\\s+([01]?\\d|2[0-9]|3[01]|\\*)\\s+(0?[1-9]|1[0-2]|\\*|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\s+([0-7]|\\*|sun|mon|tue|wed|thu|fri|sat)$";
    Pattern cronPattern = Pattern.compile(cronRegex, Pattern.CASE_INSENSITIVE);

    if (cronExpress == null || cronExpress.trim().isEmpty()) {
      return false;
    }

    return cronPattern.matcher(cronExpress).matches();
  }

  @PUT
  @Operation(
      operationId = "createOrUpdateIngestionPipeline",
      summary = "Create or update an ingestion pipeline",
      description = "Create a new ingestion pipeline, if it does not exist or update an existing ingestion pipeline.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The IngestionPipeline",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = IngestionPipeline.class))),
        @ApiResponse(responseCode = "400", description = "Bad request")
      })
  public Response createOrUpdate(
      @Context UriInfo uriInfo, @Context SecurityContext securityContext, @Valid CreateIngestionPipeline update) {
    if (!isValidCron(update.getAirflowConfig().getScheduleInterval())) {
      throw new IllegalArgumentException("schedule interval must be a cron expression");
    }

    IngestionPipeline ingestionPipeline = getIngestionPipeline(update, securityContext.getUserPrincipal().getName());
    unmask(ingestionPipeline);
    Response response = createOrUpdate(uriInfo, securityContext, ingestionPipeline);
    validateProfileSample(ingestionPipeline);
    decryptOrNullify(securityContext, (IngestionPipeline) response.getEntity(), false);
    return response;
  }

  @POST
  @Path("/deploy/{id}")
  @Operation(
      summary = "Deploy an ingestion pipeline run",
      description = "Trigger a ingestion pipeline run by Id.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The ingestion",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PipelineServiceClientResponse.class))),
        @ApiResponse(responseCode = "404", description = "Ingestion for instance {id} is not found")
      })
  public PipelineServiceClientResponse deployIngestion(
      @Context UriInfo uriInfo,
      @Parameter(description = "Id of the ingestion pipeline", schema = @Schema(type = "UUID")) @PathParam("id")
          UUID id,
      @Context SecurityContext securityContext) {
    return deployPipelineInternal(id, uriInfo, securityContext);
  }

  @POST
  @Path("/bulk/deploy")
  @Operation(
      summary = "Bulk deploy a list of Ingestion Pipeline",
      description = "Bulk deploy a list of Ingestion Pipelines given a list of IDs",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "List of Statuses of the deployed pipelines",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PipelineServiceClientResponse.class)))
      })
  public List<PipelineServiceClientResponse> bulkDeployIngestion(
      @Context UriInfo uriInfo, @Context SecurityContext securityContext, @Valid List<UUID> pipelineIdList) {

    return pipelineIdList.stream()
        .map(
            id -> {
              try {
                return deployPipelineInternal(id, uriInfo, securityContext);
              } catch (Exception e) {
                return new PipelineServiceClientResponse()
                    .withCode(500)
                    .withReason(String.format("Error deploying [%s] due to [%s]", id, e.getMessage()))
                    .withPlatform(pipelineServiceClient.getPlatform());
              }
            })
        .collect(Collectors.toList());
  }

  @POST
  @Path("/trigger/{id}")
  @Operation(
      operationId = "triggerIngestionPipelineRun",
      summary = "Trigger an ingestion pipeline run",
      description = "Trigger a ingestion pipeline run by id.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The ingestion",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PipelineServiceClientResponse.class))),
        @ApiResponse(responseCode = "404", description = "Ingestion for instance {id} is not found")
      })
  public PipelineServiceClientResponse triggerIngestion(
      @Context UriInfo uriInfo,
      @Parameter(description = "Id of the ingestion pipeline", schema = @Schema(type = "UUID")) @PathParam("id")
          UUID id,
      @Context SecurityContext securityContext) {
    Fields fields = getFields(FIELD_OWNER);
    IngestionPipeline ingestionPipeline = repository.get(uriInfo, id, fields);
    ingestionPipeline.setOpenMetadataServerConnection(
        new OpenMetadataConnectionBuilder(openMetadataApplicationConfig).build());
    decryptOrNullify(securityContext, ingestionPipeline, true);
    ServiceEntityInterface service = Entity.getEntity(ingestionPipeline.getService(), "", Include.NON_DELETED);
    return pipelineServiceClient.runPipeline(ingestionPipeline, service);
  }

  @POST
  @Path("/toggleIngestion/{id}")
  @Operation(
      operationId = "toggleIngestionPipelineEnabled",
      summary = "Set an ingestion pipeline either as enabled or disabled",
      description = "Toggle an ingestion pipeline state by Id.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The ingestion",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = IngestionPipeline.class))),
        @ApiResponse(responseCode = "404", description = "Ingestion for instance {id} is not found")
      })
  public Response toggleIngestion(
      @Context UriInfo uriInfo,
      @Parameter(description = "Id of the ingestion pipeline", schema = @Schema(type = "UUID")) @PathParam("id")
          UUID id,
      @Context SecurityContext securityContext) {
    Fields fields = getFields(FIELD_OWNER);
    IngestionPipeline pipeline = repository.get(uriInfo, id, fields);
    // This call updates the state in Airflow as well as the `enabled` field on the IngestionPipeline
    decryptOrNullify(securityContext, pipeline, true);
    pipelineServiceClient.toggleIngestion(pipeline);
    Response response = createOrUpdate(uriInfo, securityContext, pipeline);
    decryptOrNullify(securityContext, (IngestionPipeline) response.getEntity(), false);
    return response;
  }

  @POST
  @Path("/kill/{id}")
  @Operation(
      operationId = "killIngestionPipelineRuns",
      summary = "Mark as failed and kill any not-finished workflow or task for the ingestion pipeline",
      description = "Kill an ingestion pipeline by Id.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The ingestion",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PipelineServiceClientResponse.class))),
        @ApiResponse(responseCode = "404", description = "Ingestion for instance {id} is not found")
      })
  public PipelineServiceClientResponse killIngestion(
      @Context UriInfo uriInfo,
      @Parameter(description = "Id of the ingestion pipeline", schema = @Schema(type = "UUID")) @PathParam("id")
          UUID id,
      @Context SecurityContext securityContext) {
    IngestionPipeline ingestionPipeline = getInternal(uriInfo, securityContext, id, FIELDS, Include.NON_DELETED);
    decryptOrNullify(securityContext, ingestionPipeline, true);
    return pipelineServiceClient.killIngestion(ingestionPipeline);
  }

  @GET
  @Path("/ip")
  @Operation(
      operationId = "checkAirflowHostIp",
      summary = "Check the airflow REST host IP",
      description = "Check the Airflow REST host IP",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Pipeline Service host IP",
            content = @Content(mediaType = "application/json"))
      })
  public Response getHostIp(@Context UriInfo uriInfo, @Context SecurityContext securityContext) {
    return pipelineServiceClient.getHostIp();
  }

  @GET
  @Path("/status")
  @Operation(
      operationId = "checkRestAirflowStatus",
      summary = "Check the airflow REST status",
      description = "Check that the Airflow REST endpoint is reachable and up and running",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Status message",
            content = @Content(mediaType = "application/json"))
      })
  public PipelineServiceClientResponse getRESTStatus(
      @Context UriInfo uriInfo, @Context SecurityContext securityContext) {
    return pipelineServiceClient.getServiceStatus();
  }

  @DELETE
  @Path("/{id}")
  @Operation(
      operationId = "deleteIngestionPipeline",
      summary = "Delete an ingestion pipeline by Id",
      description = "Delete an ingestion pipeline by `Id`.",
      responses = {
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "404", description = "Ingestion for instance {id} is not found")
      })
  public Response delete(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(description = "Hard delete the entity. (Default = `false`)")
          @QueryParam("hardDelete")
          @DefaultValue("false")
          boolean hardDelete,
      @Parameter(description = "Id of the ingestion pipeline", schema = @Schema(type = "UUID")) @PathParam("id")
          UUID id) {
    return delete(uriInfo, securityContext, id, false, hardDelete);
  }

  @DELETE
  @Path("/name/{fqn}")
  @Operation(
      operationId = "deleteIngestionPipelineByFQN",
      summary = "Delete an ingestion pipeline by fully qualified name",
      description = "Delete an ingestion pipeline by `fullyQualifiedName`.",
      responses = {
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "404", description = "Ingestion for instance {fqn} is not found")
      })
  public Response delete(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(description = "Hard delete the entity. (Default = `false`)")
          @QueryParam("hardDelete")
          @DefaultValue("false")
          boolean hardDelete,
      @Parameter(description = "Fully qualified name of the ingestion pipeline", schema = @Schema(type = "string"))
          @PathParam("fqn")
          String fqn) {
    return deleteByName(uriInfo, securityContext, fqn, false, hardDelete);
  }

  @PUT
  @Path("/restore")
  @Operation(
      operationId = "restore",
      summary = "Restore a soft deleted ingestion pipeline",
      description = "Restore a soft deleted ingestion pipeline.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully restored the IngestionPipeline. ",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = IngestionPipeline.class)))
      })
  public Response restoreIngestionPipeline(
      @Context UriInfo uriInfo, @Context SecurityContext securityContext, @Valid RestoreEntity restore) {
    return restoreEntity(uriInfo, securityContext, restore.getId());
  }

  @GET
  @Path("/logs/{id}/last")
  @Operation(
      summary = "Retrieve all logs from last ingestion pipeline run",
      description = "Get all logs from last ingestion pipeline run by `Id`.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "JSON object with the task instance name of the ingestion on each key and log in the value",
            content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "404", description = "Logs for instance {id} is not found")
      })
  public Response getLastIngestionLogs(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(description = "Id of the ingestion pipeline", schema = @Schema(type = "UUID")) @PathParam("id")
          UUID id,
      @Parameter(description = "Returns log chunk after this cursor", schema = @Schema(type = "string"))
          @QueryParam("after")
          String after) {
    IngestionPipeline ingestionPipeline = getInternal(uriInfo, securityContext, id, FIELDS, Include.NON_DELETED);
    Map<String, String> lastIngestionLogs = pipelineServiceClient.getLastIngestionLogs(ingestionPipeline, after);
    return Response.ok(lastIngestionLogs, MediaType.APPLICATION_JSON_TYPE).build();
  }

  @PUT
  @Path("/{fqn}/pipelineStatus")
  @Operation(
      operationId = "addPipelineStatus",
      summary = "Add pipeline status",
      description = "Add pipeline status of ingestion pipeline.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully updated the PipelineStatus. ",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = IngestionPipeline.class)))
      })
  public Response addPipelineStatus(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(description = "Fully qualified name of the ingestion pipeline", schema = @Schema(type = "string"))
          @PathParam("fqn")
          String fqn,
      @Valid PipelineStatus pipelineStatus) {
    OperationContext operationContext = new OperationContext(entityType, MetadataOperation.EDIT_ALL);
    authorizer.authorize(securityContext, operationContext, getResourceContextByName(fqn));
    return repository.addPipelineStatus(uriInfo, fqn, pipelineStatus).toResponse();
  }

  @GET
  @Path("/{fqn}/pipelineStatus")
  @Operation(
      operationId = "listPipelineStatuses",
      summary = "List of pipeline status",
      description =
          "Get a list of all the pipeline status for the given ingestion pipeline id, optionally filtered by  `startTs` and `endTs` of the profile. "
              + "Use cursor-based pagination to limit the number of "
              + "entries in the list using `limit` and `before` or `after` query params.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "List of pipeline status",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = IngestionPipeline.class)))
      })
  public ResultList<PipelineStatus> listPipelineStatuses(
      @Context SecurityContext securityContext,
      @Parameter(description = "Fully qualified name of the ingestion pipeline", schema = @Schema(type = "string"))
          @PathParam("fqn")
          String fqn,
      @Parameter(
              description = "Filter pipeline status after the given start timestamp",
              schema = @Schema(type = "number"))
          @NonNull
          @QueryParam("startTs")
          Long startTs,
      @Parameter(
              description = "Filter pipeline status before the given end timestamp",
              schema = @Schema(type = "number"))
          @NonNull
          @QueryParam("endTs")
          Long endTs) {
    return repository.listPipelineStatus(fqn, startTs, endTs);
  }

  @GET
  @Path("/{fqn}/pipelineStatus/{id}")
  @Operation(
      operationId = "getPipelineStatus",
      summary = "Get pipeline status",
      description = "Get pipeline status of ingestion pipeline",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully updated state of the PipelineStatus.",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = IngestionPipeline.class)))
      })
  public PipelineStatus getPipelineStatus(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(description = "Fully qualified name of the ingestion pipeline", schema = @Schema(type = "string"))
          @PathParam("fqn")
          String fqn,
      @Parameter(description = "Id of pipeline status run", schema = @Schema(type = "string")) @PathParam("id")
          UUID runId) {
    OperationContext operationContext = new OperationContext(entityType, MetadataOperation.EDIT_ALL);
    authorizer.authorize(securityContext, operationContext, getResourceContextByName(fqn));
    return repository.getPipelineStatus(fqn, runId);
  }

  @DELETE
  @Path("/{id}/pipelineStatus")
  @Operation(
      operationId = "deletePipelineStatus",
      summary = "Delete Pipeline Status",
      tags = "ingestionPipelines",
      description = "Delete the Pipeline Status for this Ingestion Pipeline.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully deleted the Statuses",
            content =
                @Content(mediaType = "application/json", schema = @Schema(implementation = IngestionPipeline.class)))
      })
  public IngestionPipeline deletePipelineStatus(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(description = "Id of the Ingestion Pipeline", schema = @Schema(type = "UUID")) @PathParam("id")
          UUID id) {
    OperationContext operationContext = new OperationContext(entityType, MetadataOperation.DELETE);
    authorizer.authorize(securityContext, operationContext, getResourceContextById(id));
    IngestionPipeline ingestionPipeline = repository.deletePipelineStatus(id);
    return addHref(uriInfo, ingestionPipeline);
  }

  private IngestionPipeline getIngestionPipeline(CreateIngestionPipeline create, String user) {
    OpenMetadataConnection openMetadataServerConnection =
        new OpenMetadataConnectionBuilder(openMetadataApplicationConfig).build();
    return copy(new IngestionPipeline(), create, user)
        .withPipelineType(create.getPipelineType())
        .withAirflowConfig(create.getAirflowConfig())
        .withOpenMetadataServerConnection(openMetadataServerConnection)
        .withSourceConfig(create.getSourceConfig())
        .withLoggerLevel(create.getLoggerLevel())
        .withService(create.getService());
  }

  private void unmask(IngestionPipeline ingestionPipeline) {
    repository.setFullyQualifiedName(ingestionPipeline);
    IngestionPipeline originalIngestionPipeline =
        repository.findByNameOrNull(ingestionPipeline.getFullyQualifiedName(), Include.NON_DELETED);
    EntityMaskerFactory.getEntityMasker().unmaskIngestionPipeline(ingestionPipeline, originalIngestionPipeline);
  }

  private PipelineServiceClientResponse deployPipelineInternal(
      UUID id, UriInfo uriInfo, SecurityContext securityContext) {
    Fields fields = getFields(FIELD_OWNER);
    IngestionPipeline ingestionPipeline = repository.get(uriInfo, id, fields);
    ingestionPipeline.setOpenMetadataServerConnection(
        new OpenMetadataConnectionBuilder(openMetadataApplicationConfig).build());
    decryptOrNullify(securityContext, ingestionPipeline, true);
    ServiceEntityInterface service = Entity.getEntity(ingestionPipeline.getService(), "", Include.NON_DELETED);
    PipelineServiceClientResponse status = pipelineServiceClient.deployPipeline(ingestionPipeline, service);
    if (status.getCode() == 200) {
      createOrUpdate(uriInfo, securityContext, ingestionPipeline);
    }
    return status;
  }

  private void decryptOrNullify(
      SecurityContext securityContext, IngestionPipeline ingestionPipeline, boolean forceNotMask) {
    SecretsManager secretsManager = SecretsManagerFactory.getSecretsManager();
    try {
      authorizer.authorize(
          securityContext,
          new OperationContext(entityType, MetadataOperation.VIEW_ALL),
          getResourceContextById(ingestionPipeline.getId()));
    } catch (AuthorizationException e) {
      ingestionPipeline.getSourceConfig().setConfig(null);
    }
    secretsManager.decryptIngestionPipeline(ingestionPipeline);
    OpenMetadataConnection openMetadataServerConnection =
        new OpenMetadataConnectionBuilder(openMetadataApplicationConfig).build();
    ingestionPipeline.setOpenMetadataServerConnection(
        secretsManager.encryptOpenMetadataConnection(openMetadataServerConnection, false));
    if (authorizer.shouldMaskPasswords(securityContext) && !forceNotMask) {
      EntityMaskerFactory.getEntityMasker().maskIngestionPipeline(ingestionPipeline);
    }
  }
}
