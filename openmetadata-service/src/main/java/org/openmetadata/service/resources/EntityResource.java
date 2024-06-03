package org.openmetadata.service.resources;

import static org.openmetadata.common.utils.CommonUtil.listOrEmpty;
import static org.openmetadata.common.utils.CommonUtil.nullOrEmpty;
import static org.openmetadata.schema.type.MetadataOperation.CREATE;
import static org.openmetadata.schema.type.MetadataOperation.VIEW_BASIC;
import static org.openmetadata.service.util.EntityUtil.createOrUpdateOperation;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import javax.json.JsonPatch;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openmetadata.schema.CreateEntity;
import org.openmetadata.schema.EntityInterface;
import org.openmetadata.schema.type.EntityHistory;
import org.openmetadata.schema.type.EntityReference;
import org.openmetadata.schema.type.Include;
import org.openmetadata.schema.type.MetadataOperation;
import org.openmetadata.schema.type.csv.CsvImportResult;
import org.openmetadata.service.Entity;
import org.openmetadata.service.OpenMetadataApplicationConfig;
import org.openmetadata.service.exception.CatalogExceptionMessage;
import org.openmetadata.service.jdbi3.EntityRepository;
import org.openmetadata.service.jdbi3.ListFilter;
import org.openmetadata.service.security.Authorizer;
import org.openmetadata.service.security.policyevaluator.OperationContext;
import org.openmetadata.service.security.policyevaluator.ResourceContext;
import org.openmetadata.service.security.policyevaluator.ResourceContextInterface;
import org.openmetadata.service.util.EntityUtil;
import org.openmetadata.service.util.EntityUtil.Fields;
import org.openmetadata.service.util.RestUtil;
import org.openmetadata.service.util.RestUtil.DeleteResponse;
import org.openmetadata.service.util.RestUtil.PatchResponse;
import org.openmetadata.service.util.RestUtil.PutResponse;
import org.openmetadata.service.util.ResultList;

@Slf4j
public abstract class EntityResource<T extends EntityInterface, K extends EntityRepository<T>> {
  protected final Class<T> entityClass;
  protected final String entityType;
  protected final Set<String> allowedFields;
  @Getter protected final K repository;
  protected final Authorizer authorizer;
  protected final Map<String, MetadataOperation> fieldsToViewOperations = new HashMap<>();

  protected EntityResource(Class<T> entityClass, K repository, Authorizer authorizer) {
    this.entityClass = entityClass;
    entityType = repository.getEntityType();
    allowedFields = repository.getAllowedFields();
    this.repository = repository;
    this.authorizer = authorizer;
    addViewOperation("owner,followers,tags,extension", VIEW_BASIC);
    Entity.registerEntity(entityClass, entityType, repository, getEntitySpecificOperations());
  }

  /** Method used for initializing a resource, such as creating default policies, roles, etc. */
  public void initialize(OpenMetadataApplicationConfig config)
      throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
          InstantiationException, IllegalAccessException {
    // Nothing to do in the default implementation
  }

  /**
   * Method used for upgrading a resource such as adding new fields to entities, etc. that can't be done in bootstrap
   * migrate
   */
  protected void upgrade() throws IOException {
    // Nothing to do in the default implementation
  }

  public final Fields getFields(String fields) {
    return repository.getFields(fields);
  }

  public abstract T addHref(UriInfo uriInfo, T entity);

  protected List<MetadataOperation> getEntitySpecificOperations() {
    return null;
  }

  public final ResultList<T> addHref(UriInfo uriInfo, ResultList<T> list) {
    listOrEmpty(list.getData()).forEach(i -> addHref(uriInfo, i));
    return list;
  }

  public ResultList<T> listInternal(
      UriInfo uriInfo,
      SecurityContext securityContext,
      String fieldsParam,
      ListFilter filter,
      int limitParam,
      String before,
      String after) {
    Fields fields = getFields(fieldsParam);
    OperationContext listOperationContext = new OperationContext(entityType, getViewOperations(fields));
    return listInternal(
        uriInfo,
        securityContext,
        fields,
        filter,
        limitParam,
        before,
        after,
        listOperationContext,
        getResourceContext());
  }

  public ResultList<T> listInternal(
      UriInfo uriInfo,
      SecurityContext securityContext,
      Fields fields,
      ListFilter filter,
      int limitParam,
      String before,
      String after,
      OperationContext operationContext,
      ResourceContextInterface resourceContext) {
    RestUtil.validateCursors(before, after);
    authorizer.authorize(securityContext, operationContext, resourceContext);

    ResultList<T> resultList;
    if (before != null) { // Reverse paging
      resultList = repository.listBefore(uriInfo, fields, filter, limitParam, before);
    } else { // Forward paging or first page
      resultList = repository.listAfter(uriInfo, fields, filter, limitParam, after);
    }
    return addHref(uriInfo, resultList);
  }

  public T getInternal(UriInfo uriInfo, SecurityContext securityContext, UUID id, String fieldsParam, Include include) {
    Fields fields = getFields(fieldsParam);
    OperationContext operationContext = new OperationContext(entityType, getViewOperations(fields));
    return getInternal(uriInfo, securityContext, id, fields, include, operationContext, getResourceContextById(id));
  }

  public T getInternal(
      UriInfo uriInfo,
      SecurityContext securityContext,
      UUID id,
      Fields fields,
      Include include,
      OperationContext operationContext,
      ResourceContextInterface resourceContext) {
    authorizer.authorize(securityContext, operationContext, resourceContext);
    return addHref(uriInfo, repository.get(uriInfo, id, fields, include, false));
  }

  public T getVersionInternal(SecurityContext securityContext, UUID id, String version) {
    OperationContext operationContext = new OperationContext(entityType, MetadataOperation.VIEW_BASIC);
    return getVersionInternal(securityContext, id, version, operationContext, getResourceContextById(id));
  }

  public T getVersionInternal(
      SecurityContext securityContext,
      UUID id,
      String version,
      OperationContext operationContext,
      ResourceContextInterface resourceContext) {
    authorizer.authorize(securityContext, operationContext, resourceContext);
    return repository.getVersion(id, version);
  }

  protected EntityHistory listVersionsInternal(SecurityContext securityContext, UUID id) {
    OperationContext operationContext = new OperationContext(entityType, MetadataOperation.VIEW_BASIC);
    return listVersionsInternal(securityContext, id, operationContext, getResourceContextById(id));
  }

  protected EntityHistory listVersionsInternal(
      SecurityContext securityContext,
      UUID id,
      OperationContext operationContext,
      ResourceContextInterface resourceContext) {
    authorizer.authorize(securityContext, operationContext, resourceContext);
    return repository.listVersions(id);
  }

  public T getByNameInternal(
      UriInfo uriInfo, SecurityContext securityContext, String name, String fieldsParam, Include include) {
    Fields fields = getFields(fieldsParam);
    OperationContext operationContext = new OperationContext(entityType, getViewOperations(fields));
    return getByNameInternal(
        uriInfo, securityContext, name, fields, include, operationContext, getResourceContextByName(name));
  }

  public T getByNameInternal(
      UriInfo uriInfo,
      SecurityContext securityContext,
      String name,
      Fields fields,
      Include include,
      OperationContext operationContext,
      ResourceContextInterface resourceContext) {
    authorizer.authorize(securityContext, operationContext, resourceContext);
    return addHref(uriInfo, repository.getByName(uriInfo, name, fields, include, false));
  }

  public Response create(UriInfo uriInfo, SecurityContext securityContext, T entity) {
    OperationContext operationContext = new OperationContext(entityType, CREATE);
    authorizer.authorize(securityContext, operationContext, getResourceContext());
    entity = addHref(uriInfo, repository.create(uriInfo, entity));
    return Response.created(entity.getHref()).entity(entity).build();
  }

  public Response createOrUpdate(UriInfo uriInfo, SecurityContext securityContext, T entity) {
    repository.prepareInternal(entity);

    // If entity does not exist, this is a create operation, else update operation
    ResourceContext resourceContext = getResourceContextByName(entity.getFullyQualifiedName());
    OperationContext operationContext = new OperationContext(entityType, createOrUpdateOperation(resourceContext));
    authorizer.authorize(securityContext, operationContext, resourceContext);
    PutResponse<T> response = repository.createOrUpdate(uriInfo, entity);
    addHref(uriInfo, response.getEntity());
    return response.toResponse();
  }

  public Response patchInternal(UriInfo uriInfo, SecurityContext securityContext, UUID id, JsonPatch patch) {
    OperationContext operationContext = new OperationContext(entityType, patch);
    if (operationContext.getPatch().toJsonArray().size() == 0) {
      //      throw new IllegalArgumentException("This request need body");
      return Response.noContent().build();
    }
    authorizer.authorize(securityContext, operationContext, getResourceContextById(id));
    PatchResponse<T> response = repository.patch(uriInfo, id, securityContext.getUserPrincipal().getName(), patch);
    addHref(uriInfo, response.getEntity());
    return response.toResponse();
  }

  public Response delete(
      UriInfo uriInfo, SecurityContext securityContext, UUID id, boolean recursive, boolean hardDelete) {
    OperationContext operationContext = new OperationContext(entityType, MetadataOperation.DELETE);
    authorizer.authorize(securityContext, operationContext, getResourceContextById(id));
    DeleteResponse<T> response =
        repository.delete(securityContext.getUserPrincipal().getName(), id, recursive, hardDelete);
    addHref(uriInfo, response.getEntity());
    return response.toResponse();
  }

  public Response deleteByName(
      UriInfo uriInfo, SecurityContext securityContext, String name, boolean recursive, boolean hardDelete) {
    OperationContext operationContext = new OperationContext(entityType, MetadataOperation.DELETE);
    authorizer.authorize(securityContext, operationContext, getResourceContextByName(name));
    DeleteResponse<T> response =
        repository.deleteByName(securityContext.getUserPrincipal().getName(), name, recursive, hardDelete);
    addHref(uriInfo, response.getEntity());
    return response.toResponse();
  }

  public Response restoreEntity(UriInfo uriInfo, SecurityContext securityContext, UUID id) {
    OperationContext operationContext = new OperationContext(entityType, MetadataOperation.EDIT_ALL);
    authorizer.authorize(securityContext, operationContext, getResourceContextById(id));
    PutResponse<T> response = repository.restoreEntity(securityContext.getUserPrincipal().getName(), entityType, id);
    addHref(uriInfo, response.getEntity());
    LOG.info("Restored {}:{}", Entity.getEntityTypeFromObject(response.getEntity()), response.getEntity().getId());
    return response.toResponse();
  }

  public String exportCsvInternal(SecurityContext securityContext, String name) throws IOException {
    OperationContext operationContext = new OperationContext(entityType, MetadataOperation.VIEW_ALL);
    authorizer.authorize(securityContext, operationContext, getResourceContextByName(name));
    return repository.exportToCsv(name, securityContext.getUserPrincipal().getName());
  }

  protected CsvImportResult importCsvInternal(SecurityContext securityContext, String name, String csv, boolean dryRun)
      throws IOException {
    OperationContext operationContext = new OperationContext(entityType, MetadataOperation.EDIT_ALL);
    authorizer.authorize(securityContext, operationContext, getResourceContextByName(name));
    return repository.importFromCsv(name, csv, dryRun, securityContext.getUserPrincipal().getName());
  }

  public T copy(T entity, CreateEntity request, String updatedBy) {
    EntityReference owner = repository.validateOwner(request.getOwner());
    entity.setId(UUID.randomUUID());
    entity.setName(request.getName());
    entity.setDisplayName(request.getDisplayName());
    entity.setDescription(request.getDescription());
    entity.setOwner(owner);
    entity.setExtension(request.getExtension());
    entity.setUpdatedBy(updatedBy);
    entity.setUpdatedAt(System.currentTimeMillis());
    return entity;
  }

  protected ResourceContext getResourceContext() {
    return new ResourceContext(entityType);
  }

  protected ResourceContext getResourceContextById(UUID id) {
    return new ResourceContext(entityType, id, null);
  }

  protected ResourceContext getResourceContextByName(String name) {
    return new ResourceContext(entityType, null, name);
  }

  protected static final MetadataOperation[] VIEW_ALL_OPERATIONS = {MetadataOperation.VIEW_ALL};
  protected static final MetadataOperation[] VIEW_BASIC_OPERATIONS = {MetadataOperation.VIEW_BASIC};

  private MetadataOperation[] getViewOperations(Fields fields) {
    if (fields.getFieldList().isEmpty()) {
      return VIEW_BASIC_OPERATIONS;
    }
    Set<MetadataOperation> viewOperations = new TreeSet<>();
    for (String field : fields.getFieldList()) {
      MetadataOperation operation = fieldsToViewOperations.get(field);
      if (operation == null) {
        return VIEW_ALL_OPERATIONS;
      }
      viewOperations.add(operation);
    }
    return viewOperations.toArray(new MetadataOperation[0]);
  }

  protected EntityReference getEntityReference(String entityType, String fqn) {
    return EntityUtil.getEntityReference(entityType, fqn);
  }

  protected List<EntityReference> getEntityReferences(String entityType, List<String> fqns) {
    if (nullOrEmpty(fqns)) {
      return null;
    }
    return EntityUtil.getEntityReferences(entityType, fqns);
  }

  protected void addViewOperation(String fieldsParam, MetadataOperation operation) {
    String[] fields = fieldsParam.replace(" ", "").split(",");
    for (String field : fields) {
      if (allowedFields.contains(field)) {
        fieldsToViewOperations.put(field, operation);
      } else if (!"owner,followers,tags,extension".contains(field)) {
        // Some common fields for all the entities might be missing. Ignore it.
        throw new IllegalArgumentException(CatalogExceptionMessage.invalidField(field));
      }
    }
  }
}
