package software.amazon.redshiftserverless.namespace;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cloudwatch.model.InvalidParameterValueException;
import software.amazon.awssdk.services.redshift.RedshiftClient;
import software.amazon.awssdk.services.redshift.model.*;
import software.amazon.awssdk.services.redshift.model.UnsupportedOperationException;
import software.amazon.awssdk.services.redshiftserverless.RedshiftServerlessClient;
import software.amazon.awssdk.services.redshiftserverless.model.AccessDeniedException;
import software.amazon.awssdk.services.redshiftserverless.model.ConflictException;
import software.amazon.awssdk.services.redshiftserverless.model.CreateSnapshotCopyConfigurationRequest;
import software.amazon.awssdk.services.redshiftserverless.model.CreateSnapshotCopyConfigurationResponse;
import software.amazon.awssdk.services.redshiftserverless.model.DeleteSnapshotCopyConfigurationRequest;
import software.amazon.awssdk.services.redshiftserverless.model.DeleteSnapshotCopyConfigurationResponse;
import software.amazon.awssdk.services.redshiftserverless.model.GetNamespaceRequest;
import software.amazon.awssdk.services.redshiftserverless.model.GetNamespaceResponse;
import software.amazon.awssdk.services.redshiftserverless.model.InternalServerException;
import software.amazon.awssdk.services.redshiftserverless.model.ResourceNotFoundException;
import software.amazon.awssdk.services.redshiftserverless.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.redshiftserverless.model.UpdateNamespaceRequest;
import software.amazon.awssdk.services.redshiftserverless.model.UpdateNamespaceResponse;
import software.amazon.awssdk.services.redshiftserverless.model.UpdateSnapshotCopyConfigurationRequest;
import software.amazon.awssdk.services.redshiftserverless.model.UpdateSnapshotCopyConfigurationResponse;
import software.amazon.awssdk.services.redshiftserverless.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UpdateHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<RedshiftServerlessClient> proxyClient,
        final ProxyClient<RedshiftClient> redshiftProxyClient,
        final Logger logger) {

        this.logger = logger;

        final ResourceModel currentModel = request.getDesiredResourceState();
        final ResourceModel prevModel = request.getPreviousResourceState();

        // Generate the resourceModel for update request that only contains updated params
        ResourceModel tempUpdateRequestModel = currentModel.toBuilder()
                .adminUserPassword(StringUtils.equals(prevModel.getAdminUserPassword(), currentModel.getAdminUserPassword()) ? null : currentModel.getAdminUserPassword())
                .adminUsername(StringUtils.equals(prevModel.getAdminUsername(), currentModel.getAdminUsername()) ? null : currentModel.getAdminUsername())
                //TODO: we only support updating db-name after GA
//                .dbName(StringUtils.equals(prevModel.getDbName(), currentModel.getDbName()) ? null : currentModel.getDbName())
                .kmsKeyId(StringUtils.equals(prevModel.getKmsKeyId(), currentModel.getKmsKeyId()) ? null : currentModel.getKmsKeyId())
                .defaultIamRoleArn(StringUtils.equals(prevModel.getDefaultIamRoleArn(), currentModel.getDefaultIamRoleArn()) ? null : currentModel.getDefaultIamRoleArn())
                .iamRoles(compareListParamsEqualOrNot(prevModel.getIamRoles(), currentModel.getIamRoles()) ? null : currentModel.getIamRoles())
                .logExports(compareListParamsEqualOrNot(prevModel.getLogExports(), currentModel.getLogExports()) ? null : currentModel.getLogExports())
                .manageAdminPassword((prevModel.getManageAdminPassword() == currentModel.getManageAdminPassword()) ? null : currentModel.getManageAdminPassword())
                .adminPasswordSecretKmsKeyId(StringUtils.equals(prevModel.getAdminPasswordSecretKmsKeyId(), currentModel.getAdminPasswordSecretKmsKeyId()) ? null : currentModel.getAdminPasswordSecretKmsKeyId())
                .build();

        // To update the adminUserPassword or adminUserName, we need to specify both username and password in update request
        if (!StringUtils.equals(prevModel.getAdminUserPassword(), currentModel.getAdminUserPassword()) || !StringUtils.equals(prevModel.getAdminUsername(), currentModel.getAdminUsername())) {
            tempUpdateRequestModel = tempUpdateRequestModel.toBuilder()
                    .adminUsername(currentModel.getAdminUsername())
                    .adminUserPassword(currentModel.getAdminUserPassword())
                    .build();
        }

        // To update the defaultIamRole, need to specify the iam roles, we need to specify both defaultIamRole and iamRoles in update request
        if (!StringUtils.equals(prevModel.getDefaultIamRoleArn(), currentModel.getDefaultIamRoleArn())) {
            tempUpdateRequestModel = tempUpdateRequestModel.toBuilder()
                    .defaultIamRoleArn(currentModel.getDefaultIamRoleArn())
                    .iamRoles(currentModel.getIamRoles())
                    .build();
        }

        // To update the adminPasswordSecretKmsKeyId, we need to specify both manageAdminPassword and adminPasswordSecretKmsKeyId in update request
        if (!StringUtils.equals(prevModel.getAdminPasswordSecretKmsKeyId(), currentModel.getAdminPasswordSecretKmsKeyId())) {
            tempUpdateRequestModel = tempUpdateRequestModel.toBuilder()
                    .manageAdminPassword(currentModel.getManageAdminPassword())
                    .adminPasswordSecretKmsKeyId(currentModel.getAdminPasswordSecretKmsKeyId())
                    .build();
        }

        final ResourceModel updateRequestModel = tempUpdateRequestModel;
        return ProgressEvent.progress(currentModel, callbackContext)
                .then(progress ->
                        proxy.initiate("AWS-RedshiftServerless-Namespace::Update::first", proxyClient, updateRequestModel, progress.getCallbackContext())
                                .translateToServiceRequest(Translator::translateToUpdateRequest)
                                .backoffDelay(BACKOFF_STRATEGY)
                                .makeServiceCall(this::updateNamespace)
                                .stabilize((_awsRequest, _awsResponse, _client, _model, _context) -> isNamespaceActive(_client, _model, _context))
                                .handleError(this::updateNamespaceErrorHandler)
                                .progress())
                .then(progress -> {
                    progress = proxy.initiate("AWS-RedshiftServerless-Namespace::ReadOnly", proxyClient, updateRequestModel, callbackContext)
                            .translateToServiceRequest(Translator::translateToReadRequest)
                            .makeServiceCall(this::getNamespace)
                            .handleError(this::getNamespaceErrorHandler)
                            .done(awsResponse -> {
                                callbackContext.setNamespaceArn(awsResponse.namespace().namespaceArn());
                                return ProgressEvent.progress(Translator.translateFromReadResponse(awsResponse), callbackContext);
                            });
                    return progress;
                })
                .then(progress -> {
                    if (callbackContext.getNamespaceArn() != null && currentModel.getNamespaceResourcePolicy() != null)  {
                        if (currentModel.getNamespaceResourcePolicy().isEmpty()) {
                            if (request.getPreviousResourceState().getNamespaceResourcePolicy() != null) {
                                return proxy.initiate("AWS-Redshift-ResourcePolicy::Delete", redshiftProxyClient, updateRequestModel, callbackContext)
                                        .translateToServiceRequest(resourceModelRequest -> Translator.translateToDeleteResourcePolicyRequest(resourceModelRequest, callbackContext.getNamespaceArn()))
                                        .makeServiceCall(this::deleteNamespaceResourcePolicy)
                                        .progress();
                            }
                        }
                        else {
                            return proxy.initiate("AWS-Redshift-ResourcePolicy::Update", redshiftProxyClient, updateRequestModel, callbackContext)
                                    .translateToServiceRequest(resourceModel -> Translator.translateToPutResourcePolicy(resourceModel, callbackContext.getNamespaceArn(), logger))
                                    .makeServiceCall(this::putNamespaceResourcePolicy)
                                    .progress();
                        }
                    }
                    return progress;
                })
                .then(progress -> {
                    Map<String, SnapshotCopyConfiguration> desiredSnapshotCopyConfigurations = currentModel.getSnapshotCopyConfigurations()
                            .stream()
                            .collect(Collectors.toMap(SnapshotCopyConfiguration::getDestinationRegion, Function.identity()));

                    // We currently only support CRC for 1 destination region per namespace
                    if (desiredSnapshotCopyConfigurations.size() > 1) {
                        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                            .status(OperationStatus.FAILED)
                            .errorCode(HandlerErrorCode.InvalidRequest)
                            .message(String.format("You can only have one snapshot copy configuration per namespace %s", currentModel.getNamespace().getNamespaceName()))
                            .build();
                    }

                    // Determine snapshot copy configurations to delete, update & create
                    Map<String, software.amazon.awssdk.services.redshiftserverless.model.SnapshotCopyConfiguration> existingSnapshotCopyConfigurations = getSnapshotCopyConfigurations(proxyClient, currentModel);
                    List<SnapshotCopyConfiguration> configurationsToCreate = SetUtils.difference(desiredSnapshotCopyConfigurations.keySet(), existingSnapshotCopyConfigurations.keySet())
                            .stream()
                            .map(desiredSnapshotCopyConfigurations::get)
                            .collect(Collectors.toList());
                    List<software.amazon.awssdk.services.redshiftserverless.model.SnapshotCopyConfiguration> configurationsToDelete = SetUtils.difference(existingSnapshotCopyConfigurations.keySet(), desiredSnapshotCopyConfigurations.keySet())
                            .stream()
                            .map(existingSnapshotCopyConfigurations::get)
                            .collect(Collectors.toList());
                    Map<String, SnapshotCopyConfiguration> configurationsToUpdate = new HashMap<>();
                    SetUtils.intersection(desiredSnapshotCopyConfigurations.keySet(), existingSnapshotCopyConfigurations.keySet())
                            .forEach(destinationRegion -> {
                                SnapshotCopyConfiguration desired = desiredSnapshotCopyConfigurations.get(destinationRegion);
                                software.amazon.awssdk.services.redshiftserverless.model.SnapshotCopyConfiguration existing = existingSnapshotCopyConfigurations.get(destinationRegion);

                                // If destination kms key has changed, then we need to delete and re-create the snapshot copy configuration
                                if (ObjectUtils.notEqual(desired.getDestinationKmsKeyId(), existing.destinationKmsKeyId())) {
                                    configurationsToDelete.add(existing);
                                    configurationsToCreate.add(desired);
                                }

                                // If retention period has changed, then we update the snapshot copy configuration
                                if (ObjectUtils.notEqual(desired.getSnapshotRetentionPeriod(), existing.snapshotRetentionPeriod())) {
                                    configurationsToUpdate.put(existing.snapshotCopyConfigurationId(), desired);
                                }
                            });

                    // 1. Delete snapshot copy configurations
                    for (software.amazon.awssdk.services.redshiftserverless.model.SnapshotCopyConfiguration snapshotCopyConfiguration : configurationsToDelete) {
                        progress = progress.then(__ -> proxy.initiate(String.format("AWS-RedshiftServerless-Namespace::DeleteSnapshotCopyConfiguration::%s", snapshotCopyConfiguration.destinationRegion()), proxyClient, currentModel, callbackContext)
                                .translateToServiceRequest((model) -> Translator.translateToDeleteSnapshotCopyConfigurationRequest(model, snapshotCopyConfiguration.snapshotCopyConfigurationId()))
                                .makeServiceCall(this::deleteSnapshotCopyConfiguration)
                                .progress());
                    }

                    // 2. Update snapshot copy configurations
                    for (Map.Entry<String, SnapshotCopyConfiguration> entry : configurationsToUpdate.entrySet()) {
                        progress = progress.then(__ -> proxy.initiate(String.format("AWS-RedshiftServerless-Namespace::UpdateSnapshotCopyConfiguration::%s", entry.getValue().getDestinationRegion()), proxyClient, currentModel, callbackContext)
                                .translateToServiceRequest((model) -> Translator.translateToUpdateSnapshotCopyConfigurationRequest(model, entry.getKey(), entry.getValue()))
                                .makeServiceCall(this::updateSnapshotCopyConfiguration)
                                .progress());
                    }

                    // 3. Create snapshot copy configurations
                    for (SnapshotCopyConfiguration snapshotCopyConfiguration : configurationsToCreate) {
                        progress = progress.then(__ -> proxy.initiate(String.format("AWS-RedshiftServerless-Namespace::CreateSnapshotCopyConfiguration::%s", snapshotCopyConfiguration.getDestinationRegion()), proxyClient, currentModel, callbackContext)
                                .translateToServiceRequest((model) -> Translator.translateToCreateSnapshotCopyConfigurationRequest(model, snapshotCopyConfiguration))
                                .makeServiceCall(this::createSnapshotCopyConfiguration)
                                .progress());
                    }

                    return progress;
                })
                .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, redshiftProxyClient, logger));
    }

    private UpdateNamespaceResponse updateNamespace(final UpdateNamespaceRequest updateNamespaceRequest,
                                                    final ProxyClient<RedshiftServerlessClient> proxyClient) {
        UpdateNamespaceResponse updateNamespaceResponse = null;

        logger.log(String.format("%s %s updateNamespace.", ResourceModel.TYPE_NAME, updateNamespaceRequest.namespaceName()));
        updateNamespaceResponse = proxyClient.injectCredentialsAndInvokeV2(updateNamespaceRequest, proxyClient.client()::updateNamespace);
        logger.log(String.format("%s %s update namespace issued.", ResourceModel.TYPE_NAME,
                updateNamespaceRequest.namespaceName()));
        return updateNamespaceResponse;
    }

    private GetNamespaceResponse getNamespace(final GetNamespaceRequest getNamespaceRequest,
                                              final ProxyClient<RedshiftServerlessClient> proxyClient) {
        GetNamespaceResponse getNamespaceResponse = null;

        logger.log(String.format("%s %s getNamespaces.", ResourceModel.TYPE_NAME, getNamespaceRequest.namespaceName()));
        getNamespaceResponse = proxyClient.injectCredentialsAndInvokeV2(getNamespaceRequest, proxyClient.client()::getNamespace);
        logger.log(String.format("%s %s has successfully been read.", ResourceModel.TYPE_NAME, getNamespaceRequest.namespaceName()));
        return getNamespaceResponse;
    }

    private PutResourcePolicyResponse putNamespaceResourcePolicy(
            final PutResourcePolicyRequest putRequest,
            final ProxyClient<RedshiftClient> proxyClient) {
        PutResourcePolicyResponse putResponse = null;

        try {
            putResponse = proxyClient.injectCredentialsAndInvokeV2(putRequest, proxyClient.client()::putResourcePolicy);
        } catch (ResourceNotFoundException e){
            throw new CfnNotFoundException(e);
        } catch (InvalidPolicyException | UnsupportedOperationException | InvalidParameterValueException e) {
            throw new CfnInvalidRequestException(ResourceModel.TYPE_NAME, e);
        } catch (SdkClientException | RedshiftException  e) {
            throw new CfnGeneralServiceException(ResourceModel.TYPE_NAME, e);
        }

        logger.log(String.format("%s successfully put resource policy.", ResourceModel.TYPE_NAME));
        return putResponse;
    }

    private DeleteResourcePolicyResponse deleteNamespaceResourcePolicy(
            final DeleteResourcePolicyRequest deleteRequest,
            final ProxyClient<RedshiftClient> proxyClient) {
        DeleteResourcePolicyResponse deleteResponse = null;

        try{
            deleteResponse = proxyClient.injectCredentialsAndInvokeV2(deleteRequest, proxyClient.client()::deleteResourcePolicy);
        } catch (ResourceNotFoundException e){
            throw new CfnNotFoundException(e);
        } catch ( UnsupportedOperationException e) {
            throw new CfnInvalidRequestException(ResourceModel.TYPE_NAME, e);
        } catch (SdkClientException | RedshiftException e) {
            throw new CfnGeneralServiceException(ResourceModel.TYPE_NAME, e);
        }

        logger.log(String.format("%s successfully deleted resource policy.", ResourceModel.TYPE_NAME));
        return deleteResponse;
    }

    private CreateSnapshotCopyConfigurationResponse createSnapshotCopyConfiguration(final CreateSnapshotCopyConfigurationRequest createRequest,
                                                                                    final ProxyClient<RedshiftServerlessClient> proxyClient) {
        CreateSnapshotCopyConfigurationResponse createResponse = null;

        try {
            createResponse = proxyClient.injectCredentialsAndInvokeV2(createRequest, proxyClient.client()::createSnapshotCopyConfiguration);
        } catch (final ValidationException e) {
            throw new CfnInvalidRequestException(e);
        } catch (final ConflictException e) {
            throw new CfnResourceConflictException(e);
        } catch (final AccessDeniedException e) {
            throw new CfnAccessDeniedException(e);
        } catch (final ServiceQuotaExceededException e) {
            throw new CfnServiceLimitExceededException(e);
        } catch (final ResourceNotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, createRequest.namespaceName());
        } catch (final InternalServerException e) {
            throw new CfnServiceInternalErrorException(e);
        } catch (SdkClientException e) {
            throw new CfnGeneralServiceException(e);
        }

        logger.log(String.format("Created snapshot copy configuration for %s %s in destination region %s.", ResourceModel.TYPE_NAME,
                createResponse.snapshotCopyConfiguration().namespaceName(), createResponse.snapshotCopyConfiguration().destinationRegion()));
        return createResponse;
    }

    private UpdateSnapshotCopyConfigurationResponse updateSnapshotCopyConfiguration(final UpdateSnapshotCopyConfigurationRequest updateRequest,
                                                                                    final ProxyClient<RedshiftServerlessClient> proxyClient) {
        UpdateSnapshotCopyConfigurationResponse updateResponse = null;

        try {
            updateResponse = proxyClient.injectCredentialsAndInvokeV2(updateRequest, proxyClient.client()::updateSnapshotCopyConfiguration);
        } catch (final ValidationException e) {
            throw new CfnInvalidRequestException(e);
        } catch (final ConflictException e) {
            throw new CfnResourceConflictException(e);
        } catch (final AccessDeniedException e) {
            throw new CfnAccessDeniedException(e);
        } catch (final ResourceNotFoundException e) {
            throw new CfnNotFoundException(e);
        } catch (final InternalServerException e) {
            throw new CfnServiceInternalErrorException(e);
        } catch (SdkClientException e) {
            throw new CfnGeneralServiceException(e);
        }

        logger.log(String.format("Updated snapshot copy configuration for %s %s in destination region %s.", ResourceModel.TYPE_NAME,
                updateResponse.snapshotCopyConfiguration().namespaceName(), updateResponse.snapshotCopyConfiguration().destinationRegion()));
        return updateResponse;
    }

    private DeleteSnapshotCopyConfigurationResponse deleteSnapshotCopyConfiguration(final DeleteSnapshotCopyConfigurationRequest deleteRequest,
                                                                                    final ProxyClient<RedshiftServerlessClient> proxyClient) {
        DeleteSnapshotCopyConfigurationResponse deleteResponse = null;

        try {
            deleteResponse = proxyClient.injectCredentialsAndInvokeV2(deleteRequest, proxyClient.client()::deleteSnapshotCopyConfiguration);
        } catch (final ValidationException e) {
            throw new CfnInvalidRequestException(e);
        } catch (final ConflictException e) {
            throw new CfnResourceConflictException(e);
        } catch (final AccessDeniedException e) {
            throw new CfnAccessDeniedException(e);
        } catch (final ResourceNotFoundException e) {
            logger.log(String.format("Snapshot copy configuration id %s does not exist.", deleteRequest.snapshotCopyConfigurationId()));
            return null;
        } catch (final InternalServerException e) {
            throw new CfnServiceInternalErrorException(e);
        } catch (SdkClientException e) {
            throw new CfnGeneralServiceException(e);
        }

        logger.log(String.format("Deleted snapshot copy configuration for %s %s in destination region %s.", ResourceModel.TYPE_NAME,
                deleteResponse.snapshotCopyConfiguration().namespaceName(), deleteResponse.snapshotCopyConfiguration().destinationRegion()));
        return deleteResponse;
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateNamespaceErrorHandler(final UpdateNamespaceRequest updateNamespaceRequest,
                                                                                      final Exception exception,
                                                                                      final ProxyClient<RedshiftServerlessClient> client,
                                                                                      final ResourceModel model,
                                                                                      final CallbackContext context) {
        return errorHandler(exception);
    }

    private ProgressEvent<ResourceModel, CallbackContext> getNamespaceErrorHandler(final GetNamespaceRequest getNamespaceRequest,
                                                                                   final Exception exception,
                                                                                   final ProxyClient<RedshiftServerlessClient> client,
                                                                                   final ResourceModel model,
                                                                                   final CallbackContext context) {
        return errorHandler(exception);
    }

    private boolean compareListParamsEqualOrNot(List<String> prevParam, List<String> currParam) {
        if ((prevParam == null || prevParam.isEmpty()) && (currParam == null || currParam.isEmpty())) {
            return true;
        } else if (prevParam != null && currParam != null) {
            return CollectionUtils.isEqualCollection(prevParam, currParam);
        }
        return false;
    }
}
