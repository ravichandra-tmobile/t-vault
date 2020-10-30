/** *******************************************************************************
*  Copyright 2020 T-Mobile, US
*
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
*  See the readme.txt file for additional language around disclaimer of warranties.
*********************************************************************************** */
package com.tmobile.cso.vault.api.service;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tmobile.cso.vault.api.model.*;
import com.tmobile.cso.vault.api.utils.*;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.tmobile.cso.vault.api.common.IAMServiceAccountConstants;
import com.tmobile.cso.vault.api.common.TVaultConstants;
import com.tmobile.cso.vault.api.controller.ControllerUtil;
import com.tmobile.cso.vault.api.controller.OIDCUtil;

import com.tmobile.cso.vault.api.process.RequestProcessor;
import com.tmobile.cso.vault.api.process.Response;

@SuppressWarnings("unchecked")
@RunWith(PowerMockRunner.class)
@ComponentScan(basePackages = { "com.tmobile.cso.vault.api" })
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@PrepareForTest({ ControllerUtil.class, JSONUtil.class, PolicyUtils.class, OIDCUtil.class})
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*" })
public class IAMServiceAccountServiceTest {

	@InjectMocks
	IAMServiceAccountsService iamServiceAccountsService;

	@Mock
	private RequestProcessor reqProcessor;

	@Mock
	AccessService accessService;

	String token;

	@Mock
	UserDetails userDetails;

	@Mock
    PolicyUtils policyUtils;
    
    @Mock
    OIDCUtil OIDCUtil;
    
    @Mock
    AppRoleService appRoleService;
    
    @Mock
    TokenUtils tokenUtils;
    
    @Mock
	EmailUtils emailUtils;

    @Mock
	IAMServiceAccountUtils iamServiceAccountUtils;

    @Mock
    DirectoryService directoryService;

    @Before
    public void setUp()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        PowerMockito.mockStatic(ControllerUtil.class);
        PowerMockito.mockStatic(OIDCUtil.class);
        PowerMockito.mockStatic(JSONUtil.class);

        Whitebox.setInternalState(ControllerUtil.class, "log", LogManager.getLogger(ControllerUtil.class));
        Whitebox.setInternalState(OIDCUtil.class, "log", LogManager.getLogger(OIDCUtil.class));
        when(JSONUtil.getJSON(Mockito.any(ImmutableMap.class))).thenReturn("log");
        ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "ldap");
		ReflectionTestUtils.setField(iamServiceAccountsService, "iamMasterPolicyName", "iamportal_master_policy");
        Map<String, String> currentMap = new HashMap<>();
        currentMap.put("apiurl", "http://localhost:8080/vault/v2/identity");
        currentMap.put("user", "");
        ThreadLocalContext.setCurrentMap(currentMap);
    }

    Response getMockResponse(HttpStatus status, boolean success, String expectedBody) {
        Response response = new Response();
        response.setHttpstatus(status);
        response.setSuccess(success);
        if (!expectedBody.equals("")) {
            response.setResponse(expectedBody);
        }
        return response;
    }

	UserDetails getMockUser(boolean isAdmin) {
		token = "5PDrOhsy4ig8L3EpsJZSLAMg";
		userDetails = new UserDetails();
		userDetails.setUsername("normaluser");
		userDetails.setAdmin(isAdmin);
		userDetails.setClientToken(token);
		userDetails.setSelfSupportToken(token);
		return userDetails;
	}

	private IAMServiceAccount generateIAMServiceAccount(String userName, String awsAccountId, String owner) {
		IAMServiceAccount iamServiceAccount = new IAMServiceAccount();
		iamServiceAccount.setUserName(userName);
		iamServiceAccount.setAwsAccountId(awsAccountId);
		iamServiceAccount.setAwsAccountName("testaccount1");
		iamServiceAccount.setOwnerNtid(owner);
		iamServiceAccount.setOwnerEmail("normaluser@testmail.com");
		iamServiceAccount.setApplicationId("app1");
		iamServiceAccount.setApplicationName("App1");
		iamServiceAccount.setApplicationTag("App1");
		iamServiceAccount.setCreatedAtEpoch(12345L);
		iamServiceAccount.setSecret(generateIAMSecret());
		return iamServiceAccount;
	}

	private List<IAMSecrets> generateIAMSecret() {
		List<IAMSecrets> iamSecrets = new ArrayList<>();
		IAMSecrets iamSecret = new IAMSecrets();
		iamSecret.setAccessKeyId("testaccesskey555");
		iamSecret.setExpiryDuration(7776000000L);
		iamSecrets.add(iamSecret);
		return iamSecrets;
	}

	public String getJSON(Object obj) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			return TVaultConstants.EMPTY_JSON;
		}
	}

	private IAMServiceAccountMetadataDetails populateIAMSvcAccMetaData(IAMServiceAccount iamServiceAccount) {

		IAMServiceAccountMetadataDetails iamServiceAccountMetadataDetails = new IAMServiceAccountMetadataDetails();
		List<IAMSecretsMetadata> iamSecretsMetadatas = new ArrayList<>();
		iamServiceAccountMetadataDetails.setUserName(iamServiceAccount.getUserName());
		iamServiceAccountMetadataDetails.setAwsAccountId(iamServiceAccount.getAwsAccountId());
		iamServiceAccountMetadataDetails.setAwsAccountName(iamServiceAccount.getAwsAccountName());
		iamServiceAccountMetadataDetails.setApplicationId(iamServiceAccount.getApplicationId());
		iamServiceAccountMetadataDetails.setApplicationName(iamServiceAccount.getApplicationName());
		iamServiceAccountMetadataDetails.setApplicationTag(iamServiceAccount.getApplicationTag());
		iamServiceAccountMetadataDetails.setCreatedAtEpoch(iamServiceAccount.getCreatedAtEpoch());
		iamServiceAccountMetadataDetails.setOwnerEmail(iamServiceAccount.getOwnerEmail());
		iamServiceAccountMetadataDetails.setOwnerNtid(iamServiceAccount.getOwnerNtid());
		for (IAMSecrets iamSecrets : iamServiceAccount.getSecret()) {
			IAMSecretsMetadata iamSecretsMetadata = new IAMSecretsMetadata();
			iamSecretsMetadata.setAccessKeyId(iamSecrets.getAccessKeyId());
			iamSecretsMetadata.setExpiryDuration(iamSecrets.getExpiryDuration());
			iamSecretsMetadatas.add(iamSecretsMetadata);
		}
		iamServiceAccountMetadataDetails.setSecret(iamSecretsMetadatas);

		return iamServiceAccountMetadataDetails;
	}

    @Test
    public void test_getIAMServiceAccountsList_successfully() {
        userDetails = getMockUser(false);
        token = userDetails.getClientToken();
        String [] policies = {"r_users_s1", "w_users_s2", "r_shared_s3", "w_shared_s4", "r_apps_s5", "w_apps_s6", "d_apps_s7", "w_svcacct_test", "r_iamsvcacc_1234567890_test"};
        ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK).body("{\"shared\":[{\"s3\":\"read\"},{\"s4\":\"write\"}],\"users\":[{\"s1\":\"read\"},{\"s2\":\"write\"}],\"svcacct\":[{\"test\":\"read\"}],\"iamsvcacc\":[{\"test\":\"read\"}],\"apps\":[{\"s5\":\"read\"},{\"s6\":\"write\"},{\"s7\":\"deny\"}]}");
       
        when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);
        when(JSONUtil.getJSON(Mockito.any())).thenReturn("{\"shared\":[{\"s3\":\"read\"},{\"s4\":\"write\"}],\"users\":[{\"s1\":\"read\"},{\"s2\":\"write\"}],\"svcacct\":[{\"test\":\"read\"}],\"iamsvcacc\":[{\"test\":\"read\"}],\"apps\":[{\"s5\":\"read\"},{\"s6\":\"write\"},{\"s7\":\"deny\"}]}");
        ResponseEntity<String> responseEntity = iamServiceAccountsService.getIAMServiceAccountsList(userDetails, token);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(responseEntityExpected, responseEntity);
    }
    
    @Test
    public void test_AssociateAppRole_succssfully() throws Exception {

        ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK).body("{\"messages\":[\"Approle successfully associated with IAM Service Account\"]}");
        String token = "5PDrOhsy4ig8L3EpsJZSLAMg";
        UserDetails userDetails = getMockUser(false);
        IAMServiceAccountApprole serviceAccountApprole = new IAMServiceAccountApprole("testsvcname", "role1", "rotate", "1234567890");

        String [] policies = {"o_iamsvcacc_1234567890_testsvcname"};
        when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);
        Response appRoleResponse = getMockResponse(HttpStatus.OK, true, "{\"data\": {\"policies\":\"w_iamsvcacc_testsvcname\"}}");
        when(reqProcessor.process("/auth/approle/role/read","{\"role_name\":\"role1\"}",token)).thenReturn(appRoleResponse);
        Response configureAppRoleResponse = getMockResponse(HttpStatus.OK, true, "");
        when(appRoleService.configureApprole(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(configureAppRoleResponse);
        Response updateMetadataResponse = getMockResponse(HttpStatus.NO_CONTENT, true, "");
        when(ControllerUtil.updateMetadata(Mockito.anyMap(),Mockito.anyString())).thenReturn(updateMetadataResponse);

        when(tokenUtils.getSelfServiceToken()).thenReturn(token);
        when(reqProcessor.process(eq("/sdb"),Mockito.any(),eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true, "{\"data\":{\"initialPasswordReset\":true,\"managedBy\":\"smohan11\",\"name\":\"svc_vault_test5\",\"users\":{\"smohan11\":\"sudo\"}}}"));
        ResponseEntity<String> responseEntityActual =  iamServiceAccountsService.associateApproletoIAMsvcacc(userDetails, token, serviceAccountApprole);

        assertEquals(HttpStatus.OK, responseEntityActual.getStatusCode());
        assertEquals(responseEntityExpected, responseEntityActual);

    }

	@Test
	public void testOnboardIAMServiceAccountSuccss() {
		userDetails = getMockUser(true);
		token = userDetails.getClientToken();
		IAMServiceAccount serviceAccount = generateIAMServiceAccount("testaccount", "1234567", "normaluser");
		String iamSvcAccName = serviceAccount.getAwsAccountId() + "_" + serviceAccount.getUserName();
		String iamSvccAccPath = IAMServiceAccountConstants.IAM_SVCC_ACC_PATH + iamSvcAccName;

		String metaDataStr = "{ \"data\": {}, \"path\": \"iamsvcacc/1234567_testaccount\"}";
		String metadatajson = "{\"path\":\"iamsvcacc/1234567_testaccount\",\"data\":{}}";

		String iamMetaDataStr = "{ \"data\": {\"userName\": \"testaccount\", \"awsAccountId\": \"1234567\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 12345L, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345L}]}, \"path\": \"iamsvcacc/1234567_testaccount\"}";
		String iamMetaDatajson = "{\"path\":\"iamsvcacc/1234567_testaccount\",\"data\": {\"userName\": \"testaccount\", \"awsAccountId\": \"1234567\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 12345L, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345L}]}}";

		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");

		Map<String, Object> iamSvcAccPolicyMap = new HashMap<>();
		iamSvcAccPolicyMap.put("isActivated", false);

		IAMServiceAccountMetadataDetails iamServiceAccountMetadataDetails = populateIAMSvcAccMetaData(serviceAccount);
		IAMSvccAccMetadata iamSvccAccMetadata = new IAMSvccAccMetadata(iamSvccAccPath,
				iamServiceAccountMetadataDetails);

		when(reqProcessor.process(eq("/iam/onboardedlist"), Mockito.any(), eq(token))).thenReturn(getMockResponse(
				HttpStatus.OK, true, "{\"keys\":[\"12234237890_svc_tvt_test13\",\"1223455345_svc_tvt_test9\"]}"));

		when(JSONUtil.getJSON(Mockito.any())).thenReturn(metaDataStr);
		when(ControllerUtil.parseJson(metaDataStr)).thenReturn(iamSvcAccPolicyMap);
		when(ControllerUtil.convetToJson(iamSvcAccPolicyMap)).thenReturn(metadatajson);
		when(reqProcessor.process("/write", metadatajson, token)).thenReturn(responseNoContent);

		// create metadata
		when(JSONUtil.getJSON(iamSvccAccMetadata)).thenReturn(iamMetaDataStr);
		Map<String, Object> rqstParams = new HashMap<>();
		rqstParams.put("isActivated", false);
		rqstParams.put("userName", "testaccount");
		rqstParams.put("awsAccountId", "1234567");
		rqstParams.put("awsAccountName", "testaccount1");
		rqstParams.put("createdAtEpoch", 12345L);
		rqstParams.put("owner_ntid", "normaluser");
		rqstParams.put("owner_email", "normaluser@testmail.com");
		rqstParams.put("application_id", "app1");
		rqstParams.put("application_name", "App1");

		when(ControllerUtil.parseJson(iamMetaDataStr)).thenReturn(rqstParams);
		when(ControllerUtil.convetToJson(rqstParams)).thenReturn(iamMetaDatajson);
		when(ControllerUtil.createMetadata(any(), eq(token))).thenReturn(true);

		// CreateIAMServiceAccountPolicies
		ResponseEntity<String> createPolicyResponse = ResponseEntity.status(HttpStatus.OK)
				.body("{\"messages\":[\"Successfully created policies for IAM service account\"]}");
		when(accessService.createPolicy(Mockito.anyString(), Mockito.any())).thenReturn(createPolicyResponse);

		// Add User to Service Account
		Response userResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		Response ldapConfigureResponse = getMockResponse(HttpStatus.NO_CONTENT, true, "{\"policies\":null}");
		when(reqProcessor.process("/auth/ldap/users", "{\"username\":\"normaluser\"}", token)).thenReturn(userResponse);

		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			when(ControllerUtil.getPoliciesAsListFromJson(any(), any())).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPUser(eq("normaluser"), any(), any(), eq(token)))
				.thenReturn(ldapConfigureResponse);
		when(ControllerUtil.updateMetadata(any(), any())).thenReturn(responseNoContent);

		// System under test
		String expectedResponse = "{\"messages\":[\"Successfully completed onboarding of IAM service account\"]}";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK).body(expectedResponse);

		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));

		DirectoryUser directoryUser = new DirectoryUser();
        directoryUser.setDisplayName("testUserfirstname,lastname");
        directoryUser.setGivenName("testUser");
        directoryUser.setUserEmail("testUser@t-mobile.com");
        directoryUser.setUserId("normaluser");
        directoryUser.setUserName("normaluser");

        List<DirectoryUser> persons = new ArrayList<>();
        persons.add(directoryUser);
        DirectoryObjects users = new DirectoryObjects();
        DirectoryObjectsList usersList = new DirectoryObjectsList();
        usersList.setValues(persons.toArray(new DirectoryUser[persons.size()]));
        users.setData(usersList);

        ResponseEntity<DirectoryObjects> responseEntityCorpExpected = ResponseEntity.status(HttpStatus.OK).body(users);
        when(directoryService.searchByCorpId(Mockito.any())).thenReturn(responseEntityCorpExpected);

		ReflectionTestUtils.setField(iamServiceAccountsService, "supportEmail", "support@abc.com");
		Mockito.doNothing().when(emailUtils).sendHtmlEmalFromTemplate(Mockito.any(), Mockito.any(), Mockito.any(),
				Mockito.any());
		// Mock approle permission check
		Response lookupResponse = getMockResponse(HttpStatus.OK, true, "{\"policies\":[\"iamportal_master_policy \"]}");
		when(reqProcessor.process("/auth/tvault/lookup","{}", token)).thenReturn(lookupResponse);
		List<String> currentPolicies = new ArrayList<>();
		currentPolicies.add("iamportal_master_policy");
		try {
			when(iamServiceAccountUtils.getTokenPoliciesAsListFromTokenLookupJson(Mockito.any(),Mockito.any())).thenReturn(currentPolicies);
		} catch (IOException e) {
			e.printStackTrace();
		}

		ResponseEntity<String> responseEntity = iamServiceAccountsService.onboardIAMServiceAccount(token,
				serviceAccount, userDetails);
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testRemoveUserFromIAMSvcAccLdapSuccess() {
		userDetails = getMockUser(true);
		token = userDetails.getClientToken();
		IAMServiceAccountUser iamSvcAccUser = new IAMServiceAccountUser("testaccount", "testuser1", "read", "1234567");
		Response userResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\", \"o_iamsvcacc_1234567_testaccount\"],\"ttl\":0,\"groups\":\"admin\"}}");
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "{\"policies\":null}");
		when(reqProcessor.process("/auth/ldap/users", "{\"username\":\"testuser1\"}", token)).thenReturn(userResponse);
		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			resList.add("o_iamsvcacc_1234567_testaccount");
			when(ControllerUtil.getPoliciesAsListFromJson(any(), any())).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPUser(eq("testuser1"), any(), any(), eq(token))).thenReturn(responseNoContent);
		when(ControllerUtil.updateMetadata(any(), any())).thenReturn(responseNoContent);
		// System under test
		String expectedResponse = "{\"messages\":[\"Successfully removed user from the IAM Service Account\"]}";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK).body(expectedResponse);
		String[] latestPolicies = { "o_iamsvcacc_1234567_testaccount" };
		ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "ldap");
		when(policyUtils.getCurrentPolicies(userDetails.getSelfSupportToken(), userDetails.getUsername(), userDetails))
				.thenReturn(latestPolicies);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		ResponseEntity<String> responseEntity = iamServiceAccountsService.removeUserFromIAMServiceAccount(token,
				iamSvcAccUser, userDetails);
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testRemoveUserFromIAMSvcAccOidcSuccess() {
		userDetails = getMockUser(true);
		token = userDetails.getClientToken();
		IAMServiceAccountUser iamSvcAccUser = new IAMServiceAccountUser("testaccount", "testuser1", "read", "1234567");
		Response userResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\", \"o_iamsvcacc_1234567_testaccount\"],\"ttl\":0,\"groups\":\"admin\"}}");
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "{\"policies\":null}");
		when(reqProcessor.process("/auth/ldap/users", "{\"username\":\"testuser1\"}", token)).thenReturn(userResponse);
		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			resList.add("o_iamsvcacc_1234567_testaccount");
			when(ControllerUtil.getPoliciesAsListFromJson(any(), any())).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPUser(eq("testuser1"), any(), any(), eq(token))).thenReturn(responseNoContent);
		when(ControllerUtil.updateMetadata(any(), any())).thenReturn(responseNoContent);
		// System under test
		String expectedResponse = "{\"messages\":[\"Successfully removed user from the IAM Service Account\"]}";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK).body(expectedResponse);
		String[] latestPolicies = { "o_iamsvcacc_1234567_testaccount" };
		when(policyUtils.getCurrentPolicies(userDetails.getSelfSupportToken(), userDetails.getUsername(), userDetails))
				.thenReturn(latestPolicies);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));
		// oidc test cases
		ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "oidc");
		String mountAccessor = "auth_oidc";
		DirectoryUser directoryUser = new DirectoryUser();
		directoryUser.setDisplayName("testUser1");
		directoryUser.setGivenName("testUser");
		directoryUser.setUserEmail("testUser@t-mobile.com");
		directoryUser.setUserId("testuser1");
		directoryUser.setUserName("testUser");

		List<DirectoryUser> persons = new ArrayList<>();
		persons.add(directoryUser);

		DirectoryObjects users = new DirectoryObjects();
		DirectoryObjectsList usersList = new DirectoryObjectsList();
		usersList.setValues(persons.toArray(new DirectoryUser[persons.size()]));
		users.setData(usersList);

		OIDCLookupEntityRequest oidcLookupEntityRequest = new OIDCLookupEntityRequest();
		oidcLookupEntityRequest.setId(null);
		oidcLookupEntityRequest.setAlias_id(null);
		oidcLookupEntityRequest.setName(null);
		oidcLookupEntityRequest.setAlias_name(directoryUser.getUserEmail());
		oidcLookupEntityRequest.setAlias_mount_accessor(mountAccessor);
		OIDCEntityResponse oidcEntityResponse = new OIDCEntityResponse();
		oidcEntityResponse.setEntityName("entity");
		List<String> policies = new ArrayList<>();
		policies.add("safeadmin");
		oidcEntityResponse.setPolicies(policies);
		ResponseEntity<DirectoryObjects> responseEntity1 = ResponseEntity.status(HttpStatus.OK).body(users);
		when(OIDCUtil.fetchMountAccessorForOidc(token)).thenReturn(mountAccessor);

		ResponseEntity<OIDCEntityResponse> responseEntity2 = ResponseEntity.status(HttpStatus.OK)
				.body(oidcEntityResponse);

		when(tokenUtils.getSelfServiceTokenWithAppRole()).thenReturn(token);
		String entityName = "entity";

		Response responseEntity3 = getMockResponse(HttpStatus.NO_CONTENT, true,
				"{\"data\": [\"safeadmin\",\"vaultadmin\"]]");
		when(OIDCUtil.updateOIDCEntity(any(), any())).thenReturn(responseEntity3);
		when(OIDCUtil.oidcFetchEntityDetails(any(), any(), any())).thenReturn(responseEntity2);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		ResponseEntity<String> responseEntity = iamServiceAccountsService.removeUserFromIAMServiceAccount(token,
				iamSvcAccUser, userDetails);
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testRemoveUserFromIAMSvcAccUserpassSuccess() {
		userDetails = getMockUser(true);
		token = userDetails.getClientToken();
		IAMServiceAccountUser iamSvcAccUser = new IAMServiceAccountUser("testaccount", "testuser1", "read", "1234567");
		Response userResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\", \"o_iamsvcacc_1234567_testaccount\"],\"ttl\":0,\"groups\":\"admin\"}}");
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "{\"policies\":null}");
		when(reqProcessor.process("/auth/userpass/read", "{\"username\":\"testuser1\"}", token))
				.thenReturn(userResponse);
		ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "userpass");
		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			resList.add("o_iamsvcacc_1234567_testaccount");
			when(ControllerUtil.getPoliciesAsListFromJson(any(), any())).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureUserpassUser(eq("testuser1"), any(), eq(token))).thenReturn(responseNoContent);
		when(ControllerUtil.updateMetadata(any(), any())).thenReturn(responseNoContent);
		// System under test
		String expectedResponse = "{\"messages\":[\"Successfully removed user from the IAM Service Account\"]}";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK).body(expectedResponse);
		String[] latestPolicies = { "o_iamsvcacc_1234567_testaccount" };
		when(policyUtils.getCurrentPolicies(userDetails.getSelfSupportToken(), userDetails.getUsername(), userDetails))
				.thenReturn(latestPolicies);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		ResponseEntity<String> responseEntity = iamServiceAccountsService.removeUserFromIAMServiceAccount(token,
				iamSvcAccUser, userDetails);
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testRemoveUserFromIAMSvcACcFailureNotauthorized() {
		userDetails = getMockUser(false);
		token = userDetails.getClientToken();
		IAMServiceAccountUser iamSvcAccUser = new IAMServiceAccountUser("testaccount", "testuser1", "read", "1234567");
		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			resList.add("o_iamsvcacc_1234567_testaccount");
			when(ControllerUtil.getPoliciesAsListFromJson(any(), any())).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		// System under test
		String expectedResponse = "{\"errors\":[\"Access denied: No permission to remove user from this IAM service account\"]}";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(expectedResponse);
		ResponseEntity<String> responseEntity = iamServiceAccountsService.removeUserFromIAMServiceAccount(token,
				iamSvcAccUser, userDetails);
		assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testRemoveUserFromIAMSvcAccFailure400() {
		userDetails = getMockUser(false);
		token = userDetails.getClientToken();
		IAMServiceAccountUser iamSvcAccUser = new IAMServiceAccountUser("testaccount", "testuser1", "reads", "1234567");
		// System under test
		String expectedResponse = "{\"errors\":[\"Invalid value specified for access. Valid values are read, rotate, deny\"]}";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(expectedResponse);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		ResponseEntity<String> responseEntity = iamServiceAccountsService.removeUserFromIAMServiceAccount(token,
				iamSvcAccUser, userDetails);
		assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testAddGroupToIAMSvcAccSuccessfully() {
		IAMServiceAccountGroup iamSvcAccGroup = new IAMServiceAccountGroup("testaccount", "group1", "rotate", "1234567");
		userDetails = getMockUser(false);
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK)
				.body("{\"messages\":[\"Group is successfully associated with IAM Service Account\"]}");
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");

		String[] policies = { "o_iamsvcacc_1234567_testaccount" };
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);
		Response groupResp = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\",\"w_shared_mysafe01\",\"w_shared_mysafe02\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"group1\"}", token)).thenReturn(groupResp);
		ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "ldap");
		ObjectMapper objMapper = new ObjectMapper();
		String responseJson = groupResp.getResponse();
		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			resList.add("w_shared_mysafe01");
			resList.add("w_shared_mysafe02");
			when(ControllerUtil.getPoliciesAsListFromJson(objMapper, responseJson)).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPGroup(any(), any(), any())).thenReturn(responseNoContent);
		when(ControllerUtil.updateMetadata(any(), eq(token))).thenReturn(responseNoContent);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));
		ResponseEntity<String> responseEntity = iamServiceAccountsService.addGroupToIAMServiceAccount(token,
				iamSvcAccGroup, userDetails);
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testAddGroupToIAMSvcACcOidcSuccessfully() {
		IAMServiceAccountGroup iamSvcAccGroup = new IAMServiceAccountGroup("testaccount", "group1", "rotate", "1234567");
		userDetails = getMockUser(false);
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK)
				.body("{\"messages\":[\"Group is successfully associated with IAM Service Account\"]}");
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");

		String[] policies = { "o_iamsvcacc_1234567_testaccount" };
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);
		Response groupResp = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\",\"w_shared_mysafe01\",\"w_shared_mysafe02\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"group1\"}", token)).thenReturn(groupResp);
		ObjectMapper objMapper = new ObjectMapper();
		String responseJson = groupResp.getResponse();
		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			resList.add("w_shared_mysafe01");
			resList.add("w_shared_mysafe02");
			when(ControllerUtil.getPoliciesAsListFromJson(objMapper, responseJson)).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPGroup(any(), any(), any())).thenReturn(responseNoContent);
		when(ControllerUtil.updateMetadata(any(), eq(token))).thenReturn(responseNoContent);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));

		ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "oidc");
		List<String> policie = new ArrayList<>();
		policie.add("default");
		policie.add("w_shared_mysafe02");
		policie.add("r_shared_mysafe01");
		List<String> currentpolicies = new ArrayList<>();
		currentpolicies.add("default");
		currentpolicies.add("w_shared_mysafe01");
		currentpolicies.add("w_shared_mysafe02");
		OIDCGroup oidcGroup = new OIDCGroup("123-123-123", currentpolicies);
		when(OIDCUtil.getIdentityGroupDetails("mygroup01", token)).thenReturn(oidcGroup);

		Response response = new Response();
		response.setHttpstatus(HttpStatus.NO_CONTENT);
		when(OIDCUtil.updateGroupPolicies(any(), any(), any(), any(), any())).thenReturn(response);

		ResponseEntity<String> responseEntity = iamServiceAccountsService.addGroupToIAMServiceAccount(token,
				iamSvcAccGroup, userDetails);
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testAddGroupToIAMSvcAccMetadataFailure() {
		IAMServiceAccountGroup iamSvcAccGroup = new IAMServiceAccountGroup("testaccount", "group1", "rotate", "1234567");
		userDetails = getMockUser(false);
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("{\"errors\":[\"Group configuration failed. Please try again\"]}");
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");
		Response response404 = getMockResponse(HttpStatus.NOT_FOUND, true, "");

		String[] policies = { "o_iamsvcacc_1234567_testaccount" };
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);
		Response groupResp = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\",\"w_shared_mysafe01\",\"w_shared_mysafe02\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"group1\"}", token)).thenReturn(groupResp);
		ObjectMapper objMapper = new ObjectMapper();
		String responseJson = groupResp.getResponse();
		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			resList.add("w_shared_mysafe01");
			resList.add("w_shared_mysafe02");
			when(ControllerUtil.getPoliciesAsListFromJson(objMapper, responseJson)).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPGroup(any(), any(), any())).thenReturn(responseNoContent);
		when(ControllerUtil.updateMetadata(any(), eq(token))).thenReturn(response404);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		ResponseEntity<String> responseEntity = iamServiceAccountsService.addGroupToIAMServiceAccount(token,
				iamSvcAccGroup, userDetails);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testAddGroupToIAMSvcAccFailure() {
		IAMServiceAccountGroup iamSvcAccGroup = new IAMServiceAccountGroup("testaccount", "group1", "rotate", "1234567");
		userDetails = getMockUser(true);
		token = userDetails.getClientToken();
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("{\"errors\":[\"Failed to add group to the IAM Service Account\"]}");
		Response response404 = getMockResponse(HttpStatus.NOT_FOUND, true, "");

		Response groupResp = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\",\"w_shared_mysafe01\",\"w_shared_mysafe02\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"group1\"}", token)).thenReturn(groupResp);
		ObjectMapper objMapper = new ObjectMapper();
		String responseJson = groupResp.getResponse();
		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			resList.add("w_shared_mysafe01");
			resList.add("w_shared_mysafe02");
			when(ControllerUtil.getPoliciesAsListFromJson(objMapper, responseJson)).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPGroup(any(), any(), any())).thenReturn(response404);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));
		String[] latestPolicies = { "o_iamsvcacc_1234567_testaccount" };
		when(policyUtils.getCurrentPolicies(userDetails.getSelfSupportToken(), userDetails.getUsername(), userDetails))
				.thenReturn(latestPolicies);

		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		ResponseEntity<String> responseEntity = iamServiceAccountsService.addGroupToIAMServiceAccount(token,
				iamSvcAccGroup, userDetails);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testAddGroupToIAMSvcAccFailure403() {
		IAMServiceAccountGroup iamSvcAccGroup = new IAMServiceAccountGroup("testaccount", "group1", "rotate", "1234567");
		userDetails = getMockUser(false);
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body("{\"errors\":[\"Access denied: No permission to add groups to this IAM service account\"]}");
		Response response404 = getMockResponse(HttpStatus.NOT_FOUND, true, "");

		String[] policies = { "w_iamsvcacc_1234567_testaccount" };
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);
		Response groupResp = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\",\"w_shared_mysafe01\",\"w_shared_mysafe02\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"group1\"}", token)).thenReturn(groupResp);
		ObjectMapper objMapper = new ObjectMapper();
		String responseJson = groupResp.getResponse();
		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			resList.add("w_shared_mysafe01");
			resList.add("w_shared_mysafe02");
			when(ControllerUtil.getPoliciesAsListFromJson(objMapper, responseJson)).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPGroup(any(), any(), any())).thenReturn(response404);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));
		ResponseEntity<String> responseEntity = iamServiceAccountsService.addGroupToIAMServiceAccount(token,
				iamSvcAccGroup, userDetails);
		assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testAddGroupToIAMSvcAccFailureInitialActivate() {
		IAMServiceAccountGroup iamSvcAccGroup = new IAMServiceAccountGroup("testaccount", "group1", "rotate", "1234567");
		userDetails = getMockUser(false);
		token = userDetails.getClientToken();
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
				"{\"errors\":[\"Failed to add group permission to IAM Service account. IAM Service Account is not activated. Please activate this service account and try again.\"]}");
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":false,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));
		String[] latestPolicies = { "o_iamsvcacc_1234567_testaccount" };
		when(policyUtils.getCurrentPolicies(userDetails.getSelfSupportToken(), userDetails.getUsername(), userDetails))
				.thenReturn(latestPolicies);
		ResponseEntity<String> responseEntity = iamServiceAccountsService.addGroupToIAMServiceAccount(token,
				iamSvcAccGroup, userDetails);
		assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testRemoveGroupFromIAMSvcAccSuccessfully() {
		token = userDetails.getClientToken();
		IAMServiceAccountGroup iamSvcAccGroup = new IAMServiceAccountGroup("testaccount", "group1", "rotate", "1234567");
		userDetails = getMockUser(false);
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK)
				.body("{\"messages\":[\"Group is successfully removed from IAM Service Account\"]}");
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");

		String[] policies = { "o_iamsvcacc_1234567_testaccount" };
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);
		Response groupResp = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\",\"w_shared_mysafe01\",\"w_shared_mysafe02\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"group1\"}", token)).thenReturn(groupResp);
		ObjectMapper objMapper = new ObjectMapper();
		String responseJson = groupResp.getResponse();
		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			resList.add("w_shared_mysafe01");
			resList.add("w_shared_mysafe02");
			when(ControllerUtil.getPoliciesAsListFromJson(objMapper, responseJson)).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPGroup(any(), any(), any())).thenReturn(responseNoContent);
		when(ControllerUtil.updateMetadata(any(), eq(token))).thenReturn(responseNoContent);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));

		ResponseEntity<String> responseEntity = iamServiceAccountsService.removeGroupFromIAMServiceAccount(token,
				iamSvcAccGroup, userDetails);
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testRemoveGroupFromIAMSvcAccOidcSuccessfully() {
		userDetails = getMockUser(false);
		token = userDetails.getClientToken();
		IAMServiceAccountGroup iamSvcAccGroup = new IAMServiceAccountGroup("testaccount", "group1", "rotate", "1234567");
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK)
				.body("{\"messages\":[\"Group is successfully removed from IAM Service Account\"]}");
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");

		String[] policies = { "o_iamsvcacc_1234567_testaccount" };
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);
		Response groupResp = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\",\"w_shared_mysafe01\",\"w_shared_mysafe02\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"group1\"}", token)).thenReturn(groupResp);
		ObjectMapper objMapper = new ObjectMapper();
		String responseJson = groupResp.getResponse();
		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			resList.add("w_shared_mysafe01");
			resList.add("w_shared_mysafe02");
			when(ControllerUtil.getPoliciesAsListFromJson(objMapper, responseJson)).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPGroup(any(), any(), any())).thenReturn(responseNoContent);
		when(ControllerUtil.updateMetadata(any(), eq(token))).thenReturn(responseNoContent);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));

		ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "oidc");
		List<String> policie = new ArrayList<>();
		policie.add("default");
		policie.add("w_shared_mysafe02");
		policie.add("r_shared_mysafe01");
		List<String> currentpolicies = new ArrayList<>();
		currentpolicies.add("default");
		currentpolicies.add("w_shared_mysafe01");
		currentpolicies.add("w_shared_mysafe02");
		OIDCGroup oidcGroup = new OIDCGroup("123-123-123", currentpolicies);
		when(OIDCUtil.getIdentityGroupDetails(any(), any())).thenReturn(oidcGroup);

		Response response1 = new Response();
		response1.setHttpstatus(HttpStatus.NO_CONTENT);
		when(OIDCUtil.updateGroupPolicies(any(), any(), any(), any(), any())).thenReturn(response1);

		ResponseEntity<String> responseEntity = iamServiceAccountsService.removeGroupFromIAMServiceAccount(token,
				iamSvcAccGroup, userDetails);
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testRemoveGroupFromIAMSvcAccFailureInitialActivation() {
		userDetails = getMockUser(false);
		token = userDetails.getClientToken();
		IAMServiceAccountGroup iamSvcAccGroup = new IAMServiceAccountGroup("testaccount", "group1", "rotate", "1234567");
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
				"{\"errors\":[\"Failed to remove group permission to IAM Service account. IAM Service Account is not activated. Please activate this service account and try again.\"]}");
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":false,\"managedBy\":\"smohan11\",\"name\":\"svc_vault_test5\",\"users\":{\"smohan11\":\"sudo\"}}}"));
		String[] latestPolicies = { "o_iamsvcacc_1234567_testaccount" };
		when(policyUtils.getCurrentPolicies(userDetails.getSelfSupportToken(), userDetails.getUsername(), userDetails))
				.thenReturn(latestPolicies);
		ResponseEntity<String> responseEntity = iamServiceAccountsService.removeGroupFromIAMServiceAccount(token,
				iamSvcAccGroup, userDetails);
		assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testRemoveGroupFromIAMSvcAccMetadataFailure() {
		userDetails = getMockUser(false);
		token = userDetails.getClientToken();
		IAMServiceAccountGroup iamSvcAccGroup = new IAMServiceAccountGroup("testaccount", "group1", "rotate", "1234567");
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("{\"errors\":[\"Group configuration failed. Please try again\"]}");
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");
		Response response404 = getMockResponse(HttpStatus.NOT_FOUND, true, "");

		String[] policies = { "o_iamsvcacc_1234567_testaccount" };
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);
		Response groupResp = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\",\"w_shared_mysafe01\",\"w_shared_mysafe02\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"group1\"}", token)).thenReturn(groupResp);
		ObjectMapper objMapper = new ObjectMapper();
		String responseJson = groupResp.getResponse();
		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			resList.add("w_shared_mysafe01");
			resList.add("w_shared_mysafe02");
			when(ControllerUtil.getPoliciesAsListFromJson(objMapper, responseJson)).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPGroup(any(), any(), any())).thenReturn(responseNoContent);
		when(ControllerUtil.updateMetadata(any(), eq(token))).thenReturn(response404);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));

		ResponseEntity<String> responseEntity = iamServiceAccountsService.removeGroupFromIAMServiceAccount(token,
				iamSvcAccGroup, userDetails);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testRemoveGroupFromIAMSVcAccFailure() {
		userDetails = getMockUser(true);
		token = userDetails.getClientToken();
		IAMServiceAccountGroup iamSvcAccGroup = new IAMServiceAccountGroup("testaccount", "group1", "rotate", "1234567");
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("{\"errors\":[\"Failed to remove the group from the IAM Service Account\"]}");
		Response response404 = getMockResponse(HttpStatus.NOT_FOUND, true, "");

		Response groupResp = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\",\"w_shared_mysafe01\",\"w_shared_mysafe02\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"group1\"}", token)).thenReturn(groupResp);
		ObjectMapper objMapper = new ObjectMapper();
		String responseJson = groupResp.getResponse();
		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			resList.add("w_shared_mysafe01");
			resList.add("w_shared_mysafe02");
			when(ControllerUtil.getPoliciesAsListFromJson(objMapper, responseJson)).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPGroup(any(), any(), any())).thenReturn(response404);
		String[] latestPolicies = { "o_iamsvcacc_1234567_testaccount" };
		when(policyUtils.getCurrentPolicies(userDetails.getSelfSupportToken(), userDetails.getUsername(), userDetails))
				.thenReturn(latestPolicies);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		ResponseEntity<String> responseEntity = iamServiceAccountsService.removeGroupFromIAMServiceAccount(token,
				iamSvcAccGroup, userDetails);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testRemoveGroupFromIAMSvcAccFailure403() {
		userDetails = getMockUser(false);
		token = userDetails.getClientToken();
		IAMServiceAccountGroup iamSvcAccGroup = new IAMServiceAccountGroup("testaccount", "group1", "rotate", "1234567");

		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body("{\"errors\":[\"Access denied: No permission to remove groups from this IAM service account\"]}");
		Response response404 = getMockResponse(HttpStatus.NOT_FOUND, true, "");

		String[] policies = { "w_iamsvcacc_1234567_testaccount" };
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);
		Response groupResp = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\",\"w_shared_mysafe01\",\"w_shared_mysafe02\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"group1\"}", token)).thenReturn(groupResp);
		ObjectMapper objMapper = new ObjectMapper();
		String responseJson = groupResp.getResponse();
		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			resList.add("w_shared_mysafe01");
			resList.add("w_shared_mysafe02");
			when(ControllerUtil.getPoliciesAsListFromJson(objMapper, responseJson)).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPGroup(any(), any(), any())).thenReturn(response404);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"managedBy\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"}}}"));

		ResponseEntity<String> responseEntity = iamServiceAccountsService.removeGroupFromIAMServiceAccount(token,
				iamSvcAccGroup, userDetails);
		assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}
	
	 @Test
	    public void test_removeApproleFromIAMSvcAcc_succssfully() throws Exception {

	        ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK).body("{\"messages\":[\"Approle is successfully removed from IAM Service Account\"]}");
	        String token = "5PDrOhsy4ig8L3EpsJZSLAMg";
	        UserDetails userDetails = getMockUser(false);
	        IAMServiceAccountApprole serviceAccountApprole = new IAMServiceAccountApprole("testsvcname", "role1", "rotate", "1234567890");

	        String [] policies = {"o_iamsvcacc_1234567890_testsvcname"};
	        when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);
	        Response appRoleResponse = getMockResponse(HttpStatus.OK, true, "{\"data\": {\"policies\":\"w_iamsvcacc_1234567890_testsvcname\"}}");
	        when(reqProcessor.process("/auth/approle/role/read","{\"role_name\":\"role1\"}",token)).thenReturn(appRoleResponse);
	        Response configureAppRoleResponse = getMockResponse(HttpStatus.OK, true, "");
	        when(appRoleService.configureApprole(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(configureAppRoleResponse);
	        Response updateMetadataResponse = getMockResponse(HttpStatus.NO_CONTENT, true, "");
	        when(ControllerUtil.updateMetadata(Mockito.anyMap(),Mockito.anyString())).thenReturn(updateMetadataResponse);

	        when(tokenUtils.getSelfServiceToken()).thenReturn(token);
	        when(reqProcessor.process(eq("/sdb"),Mockito.any(),eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true, "{\"data\":{\"initialPasswordReset\":true,\"managedBy\":\"smohan11\",\"name\":\"svc_vault_test5\",\"users\":{\"smohan11\":\"sudo\"}}}"));

	        ResponseEntity<String> responseEntityActual =  iamServiceAccountsService.removeApproleFromIAMSvcAcc(userDetails, token, serviceAccountApprole);

	        assertEquals(HttpStatus.OK, responseEntityActual.getStatusCode());
	        assertEquals(responseEntityExpected, responseEntityActual);

	    }
	 
	@Test
	public void test_getOnboardedIAMServiceAccounts_successfully() {
		String token = "5PDrOhsy4ig8L3EpsJZSLAMg";
		UserDetails userDetails = getMockUser(false);
		String[] policies = { "r_users_s1", "w_users_s2", "r_shared_s3", "w_shared_s4", "r_apps_s5", "w_apps_s6",
				"d_apps_s7", "w_svcacct_test", "o_iamsvcacc_1234567890_test" };
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK).body(
				"{\"keys\":{\"shared\":[{\"s3\":\"read\"},{\"s4\":\"write\"}],\"users\":[{\"s1\":\"read\"},{\"s2\":\"write\"}],\"svcacct\":[{\"test\":\"read\"}],\"iamsvcacc\":[{\"test\":\"sudo\"}],\"apps\":[{\"s5\":\"read\"},{\"s6\":\"write\"},{\"s7\":\"deny\"}]}}");

		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);
		when(JSONUtil.getJSON(Mockito.any())).thenReturn(
				"{\"shared\":[{\"s3\":\"read\"},{\"s4\":\"write\"}],\"users\":[{\"s1\":\"read\"},{\"s2\":\"write\"}],\"svcacct\":[{\"test\":\"read\"}],\"iamsvcacc\":[{\"test\":\"sudo\"}],\"apps\":[{\"s5\":\"read\"},{\"s6\":\"write\"},{\"s7\":\"deny\"}]}");
		ResponseEntity<String> responseEntity = iamServiceAccountsService.getOnboardedIAMServiceAccounts(token,
				userDetails);
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}
	
	@Test
	public void test_getIAMServiceAccountDetail_successfully() {
		String token = "5PDrOhsy4ig8L3EpsJZSLAMg";
		UserDetails userDetails = getMockUser(false);
		String iamSvcaccName = "1234567890_testiamsvc";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK).body(
				"{\"app-roles\":{\"selfserviceoidcsupportrole\":\"read\"},\"application_id\":1222,\"application_name\":\"T-Vault\",\"application_tag\":\"TVT\",\"awsAccountId\":\"123456789012\",\"awsAccountName\":\"AWS-SEC\",\"createdAtEpoch\":1086073200000,\"isActivated\":true,\"owner_email\":\"Nithin.Nazeer1@T-mobile.com\",\"owner_ntid\":\"NNazeer1\",\"secret\":[{\"accessKeyId\":\"1212zdasd\",\"expiryDuration\":\"2004-06-01 12:30:00\"}],\"userName\":\"testiamsvcacc01\",\"users\":{\"nnazeer1\":\"write\"},\"createdDate\":\"2004-06-01 12:30:00\"}");

		when(reqProcessor.process(eq("/iamsvcacct"),Mockito.any(),eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true, "{\"data\":{\"app-roles\":{\"selfserviceoidcsupportrole\":\"read\"},\"application_id\":1222,\"application_name\":\"T-Vault\",\"application_tag\":\"TVT\",\"awsAccountId\":\"123456789012\",\"awsAccountName\":\"AWS-SEC\",\"createdAtEpoch\":1086073200000,\"isActivated\":true,\"owner_email\":\"Nithin.Nazeer1@T-mobile.com\",\"owner_ntid\":\"NNazeer1\",\"secret\":[{\"accessKeyId\":\"1212zdasd\",\"expiryDuration\":\"1086073200000\"}],\"userName\":\"testiamsvcacc01\",\"users\":{\"nnazeer1\":\"write\"}}}"));
		ResponseEntity<String> responseEntity = iamServiceAccountsService.getIAMServiceAccountDetail(token, iamSvcaccName);
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
	}

	@Test
	public void test_getIAMServiceAccountSecretKey_successfully() {
		String token = "5PDrOhsy4ig8L3EpsJZSLAMg";
		String iamSvcaccName = "1234567890_testiamsvcacc01";
		String folderName = "testiamsvc_01";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK).body(
				"{\"accessKeyId\":\"1212zdasd\",\"accessKeySecret\":\"assOOetcHce1VugthF6KE9hqv2PWWbX3ULrpe1T\",\"awsAccountId\":\"123456789012\",\"expiryDateEpoch\":1609845308000,\"userName\":\"testiamsvcacc01_01\",\"expiryDate\":\"2021-01-05 16:45:08\"}");

		when(reqProcessor.process(eq("/iamsvcacct"),Mockito.any(),eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true, "{\"data\":{\"accessKeyId\":\"1212zdasd\",\"accessKeySecret\":\"assOOetcHce1VugthF6KE9hqv2PWWbX3ULrpe1T\",\"awsAccountId\":\"123456789012\",\"expiryDateEpoch\":1609845308000,\"userName\":\"testiamsvcacc01_01\",\"expiryDate\":\"2021-01-05 16:45:08\"}}"));
		ResponseEntity<String> responseEntity = iamServiceAccountsService.getIAMServiceAccountSecretKey(token, iamSvcaccName, folderName);
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
	}

	@Test
	public void test_activateIAMServiceAccount_successfull() {

		String iamServiceAccountName = "svc_vault_test5";
		String token = "123123123123";
		String awsAccountId = "1234567890";
		String path = "metadata/iamsvcacc/1234567890_svc_vault_test5";
		String iamSecret = "abcdefgh";
		String accessKeyId = "testaccesskey";
		String [] policies = {"o_iamsvcacc_1234567890_svc_vault_test5"};
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");
		String iamMetaDataStr = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		String iamMetaDataStrActivated = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": true, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";

		Response metaResponse = getMockResponse(HttpStatus.OK, true, iamMetaDataStr);
		Response metaActivatedResponse = getMockResponse(HttpStatus.OK, true, iamMetaDataStrActivated);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);

		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenAnswer(new Answer() {
			private int count = 0;

			public Object answer(InvocationOnMock invocation) {
				if (count++ == 1)
					return metaActivatedResponse;

				return metaResponse;
			}
		});

		when(reqProcessor.process("/read", "{\"path\":\""+path+"\"}", token)).thenReturn(getMockResponse(HttpStatus.OK, true,
				iamMetaDataStr));

		IAMServiceAccountSecret iamServiceAccountSecret = new IAMServiceAccountSecret(iamServiceAccountName, accessKeyId, iamSecret, 1609754282000L, awsAccountId);

		when(iamServiceAccountUtils.rotateIAMSecret(Mockito.any())).thenReturn(iamServiceAccountSecret);
		when(iamServiceAccountUtils.writeIAMSvcAccSecret(token, "iamsvcacc/1234567890_svc_vault_test5/secret_1", iamServiceAccountName, iamServiceAccountSecret)).thenReturn(true);
		when(iamServiceAccountUtils.updateIAMSvcAccNewAccessKeyIdInMetadata(eq(token), eq(awsAccountId), eq(iamServiceAccountName), eq(accessKeyId), Mockito.any())).thenReturn(responseNoContent);
		when(iamServiceAccountUtils.updateActivatedStatusInMetadata(token, iamServiceAccountName, awsAccountId)).thenReturn(responseNoContent);


		// Add User to Service Account
		Response userResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		Response ldapConfigureResponse = getMockResponse(HttpStatus.NO_CONTENT, true, "{\"policies\":null}");
		when(reqProcessor.process("/auth/ldap/users", "{\"username\":\"normaluser\"}", token)).thenReturn(userResponse);

		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			when(ControllerUtil.getPoliciesAsListFromJson(any(), any())).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPUser(eq("normaluser"), any(), any(), eq(token)))
				.thenReturn(ldapConfigureResponse);
		when(ControllerUtil.updateMetadata(any(), any())).thenReturn(responseNoContent);

		ResponseEntity<String> expectedResponse =  ResponseEntity.status(HttpStatus.OK).body("{\"messages\":[\"IAM Service account activated successfully\"]}");
		ResponseEntity<String> actualResponse = iamServiceAccountsService.activateIAMServiceAccount(token, userDetails, iamServiceAccountName, awsAccountId);
		assertEquals(expectedResponse, actualResponse);
	}

	@Test
	public void test_activateIAMServiceAccount_failed_403() {

		String iamServiceAccountName = "svc_vault_test5";
		String token = "123123123123";
		String awsAccountId = "1234567890";
		String [] policies = {"defaullt"};
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);

		ResponseEntity<String> expectedResponse =  ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"errors\":[\"Access denied: No permission to activate this IAM service account\"]}");
		ResponseEntity<String> actualResponse = iamServiceAccountsService.activateIAMServiceAccount(token, userDetails, iamServiceAccountName, awsAccountId);
		assertEquals(expectedResponse, actualResponse);
	}

	@Test
	public void test_activateIAMServiceAccount_failure_already_activated() {

		String iamServiceAccountName = "svc_vault_test5";
		String token = "123123123123";
		String awsAccountId = "1234567890";
		String [] policies = {"o_iamsvcacc_1234567890_svc_vault_test5"};
		String iamMetaDataStrActivated = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": true, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		Response metaActivatedResponse = getMockResponse(HttpStatus.OK, true, iamMetaDataStrActivated);
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);

		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(metaActivatedResponse);

		ResponseEntity<String> expectedResponse =  ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"errors\":[\"Service Account is already activated. You can now grant permissions from Permissions menu\"]}");
		ResponseEntity<String> actualResponse = iamServiceAccountsService.activateIAMServiceAccount(token, userDetails, iamServiceAccountName, awsAccountId);
		assertEquals(expectedResponse, actualResponse);
	}

	@Test
	public void test_activateIAMServiceAccount_failed_owner_association() {

		String iamServiceAccountName = "svc_vault_test5";
		String token = "123123123123";
		String awsAccountId = "1234567890";
		String path = "metadata/iamsvcacc/1234567890_svc_vault_test5";
		String iamSecret = "abcdefgh";
		String accessKeyId = "testaccesskey";
		String [] policies = {"o_iamsvcacc_1234567890_svc_vault_test5"};
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");
		String iamMetaDataStr = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		String iamMetaDataStrActivated = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": true, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";

		Response metaResponse = getMockResponse(HttpStatus.OK, true, iamMetaDataStr);
		Response metaActivatedResponse = getMockResponse(HttpStatus.OK, true, iamMetaDataStrActivated);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);

		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenAnswer(new Answer() {
			private int count = 0;

			public Object answer(InvocationOnMock invocation) {
				if (count++ == 1)
					return metaActivatedResponse;

				return metaResponse;
			}
		});

		when(reqProcessor.process("/read", "{\"path\":\""+path+"\"}", token)).thenReturn(getMockResponse(HttpStatus.OK, true,
				iamMetaDataStr));

		IAMServiceAccountSecret iamServiceAccountSecret = new IAMServiceAccountSecret(iamServiceAccountName, accessKeyId, iamSecret, 1609754282000L, awsAccountId);
		when(iamServiceAccountUtils.rotateIAMSecret(Mockito.any())).thenReturn(iamServiceAccountSecret);
		when(iamServiceAccountUtils.writeIAMSvcAccSecret(token, "iamsvcacc/1234567890_svc_vault_test5/secret_1", iamServiceAccountName, iamServiceAccountSecret)).thenReturn(true);
		when(iamServiceAccountUtils.updateIAMSvcAccNewAccessKeyIdInMetadata(eq(token), eq(awsAccountId), eq(iamServiceAccountName), eq(accessKeyId), Mockito.any())).thenReturn(responseNoContent);
		when(iamServiceAccountUtils.updateActivatedStatusInMetadata(token, iamServiceAccountName, awsAccountId)).thenReturn(responseNoContent);

		ResponseEntity<String> expectedResponse =  ResponseEntity.status(HttpStatus.OK).body("{\"errors\":[\"Failed to activate IAM Service account. IAM secrets are rotated and saved in T-Vault. However failed to add permission to owner. Owner info not found in Metadata.\"]}");
		ResponseEntity<String> actualResponse = iamServiceAccountsService.activateIAMServiceAccount(token, userDetails, iamServiceAccountName, awsAccountId);
		assertEquals(expectedResponse, actualResponse);
	}

	@Test
	public void test_activateIAMServiceAccount_failed_add_user() {

		String iamServiceAccountName = "svc_vault_test5";
		String token = "123123123123";
		String awsAccountId = "1234567890";
		String path = "metadata/iamsvcacc/1234567890_svc_vault_test5";
		String iamSecret = "abcdefgh";
		String accessKeyId = "testaccesskey";
		String [] policies = {"o_iamsvcacc_1234567890_svc_vault_test5"};
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");
		String iamMetaDataStr = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		String iamMetaDataStrActivated = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": true, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";

		Response metaResponse = getMockResponse(HttpStatus.OK, true, iamMetaDataStr);
		Response metaActivatedResponse = getMockResponse(HttpStatus.OK, true, iamMetaDataStrActivated);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);

		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenAnswer(new Answer() {
			private int count = 0;

			public Object answer(InvocationOnMock invocation) {
				if (count++ == 1)
					return metaActivatedResponse;

				return metaResponse;
			}
		});

		when(reqProcessor.process("/read", "{\"path\":\""+path+"\"}", token)).thenReturn(getMockResponse(HttpStatus.OK, true,
				iamMetaDataStr));

		IAMServiceAccountSecret iamServiceAccountSecret = new IAMServiceAccountSecret(iamServiceAccountName, accessKeyId, iamSecret, 1609754282000L, awsAccountId);

		when(iamServiceAccountUtils.rotateIAMSecret(Mockito.any())).thenReturn(iamServiceAccountSecret);
		when(iamServiceAccountUtils.writeIAMSvcAccSecret(token, "iamsvcacc/1234567890_svc_vault_test5/secret_1", iamServiceAccountName, iamServiceAccountSecret)).thenReturn(true);
		when(iamServiceAccountUtils.updateIAMSvcAccNewAccessKeyIdInMetadata(eq(token), eq(awsAccountId), eq(iamServiceAccountName), eq(accessKeyId), Mockito.any())).thenReturn(responseNoContent);
		when(iamServiceAccountUtils.updateActivatedStatusInMetadata(token, iamServiceAccountName, awsAccountId)).thenReturn(responseNoContent);


		// Add User to Service Account
		Response userResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		Response ldapConfigureResponse = getMockResponse(HttpStatus.INTERNAL_SERVER_ERROR, true, "{\"policies\":null}");
		when(reqProcessor.process("/auth/ldap/users", "{\"username\":\"normaluser\"}", token)).thenReturn(userResponse);

		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			when(ControllerUtil.getPoliciesAsListFromJson(any(), any())).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPUser(eq("normaluser"), any(), any(), eq(token)))
				.thenReturn(ldapConfigureResponse);

		ResponseEntity<String> expectedResponse =  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"errors\":[\"Failed to activate IAM Service account. IAM secrets are rotated and saved in T-Vault. However owner permission update failed.\"]}");
		ResponseEntity<String> actualResponse = iamServiceAccountsService.activateIAMServiceAccount(token, userDetails, iamServiceAccountName, awsAccountId);
		assertEquals(expectedResponse, actualResponse);
	}

	@Test
	public void test_activateIAMServiceAccount_failed_to_save_secret() {

		String iamServiceAccountName = "svc_vault_test5";
		String token = "123123123123";
		String awsAccountId = "1234567890";
		String path = "metadata/iamsvcacc/1234567890_svc_vault_test5";
		String [] policies = {"o_iamsvcacc_1234567890_svc_vault_test5"};
		String iamMetaDataStr = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		String iamMetaDataStrActivated = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": true, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";

		Response metaResponse = getMockResponse(HttpStatus.OK, true, iamMetaDataStr);
		Response metaActivatedResponse = getMockResponse(HttpStatus.OK, true, iamMetaDataStrActivated);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);

		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenAnswer(new Answer() {
			private int count = 0;

			public Object answer(InvocationOnMock invocation) {
				if (count++ == 1)
					return metaActivatedResponse;

				return metaResponse;
			}
		});

		when(reqProcessor.process("/read", "{\"path\":\""+path+"\"}", token)).thenReturn(getMockResponse(HttpStatus.OK, true,
				iamMetaDataStr));

		when(iamServiceAccountUtils.rotateIAMSecret(Mockito.any())).thenReturn(null);


		ResponseEntity<String> expectedResponse =  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"errors\":[\"Failed to activate IAM Service account. Failed to rotate secrets for one or more AccessKeyIds.\"]}");
		ResponseEntity<String> actualResponse = iamServiceAccountsService.activateIAMServiceAccount(token, userDetails, iamServiceAccountName, awsAccountId);
		assertEquals(expectedResponse, actualResponse);
	}

	@Test
	public void test_activateIAMServiceAccount_failed_metadata_update() {

		String iamServiceAccountName = "svc_vault_test5";
		String token = "123123123123";
		String awsAccountId = "1234567890";
		String path = "metadata/iamsvcacc/1234567890_svc_vault_test5";
		String iamSecret = "abcdefgh";
		String accessKeyId = "testaccesskey";
		String [] policies = {"o_iamsvcacc_1234567890_svc_vault_test5"};
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");
		String iamMetaDataStr = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		String iamMetaDataStrActivated = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": true, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";

		Response metaResponse = getMockResponse(HttpStatus.OK, true, iamMetaDataStr);
		Response metaActivatedResponse = getMockResponse(HttpStatus.OK, true, iamMetaDataStrActivated);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);

		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenAnswer(new Answer() {
			private int count = 0;

			public Object answer(InvocationOnMock invocation) {
				if (count++ == 1)
					return metaActivatedResponse;

				return metaResponse;
			}
		});

		when(reqProcessor.process("/read", "{\"path\":\""+path+"\"}", token)).thenReturn(getMockResponse(HttpStatus.OK, true,
				iamMetaDataStr));

		IAMServiceAccountSecret iamServiceAccountSecret = new IAMServiceAccountSecret(iamServiceAccountName, accessKeyId, iamSecret, 1609754282000L, awsAccountId);

		when(iamServiceAccountUtils.rotateIAMSecret(Mockito.any())).thenReturn(iamServiceAccountSecret);
		when(iamServiceAccountUtils.writeIAMSvcAccSecret(token, "iamsvcacc/1234567890_svc_vault_test5/secret_1", iamServiceAccountName, iamServiceAccountSecret)).thenReturn(true);
		when(iamServiceAccountUtils.updateIAMSvcAccNewAccessKeyIdInMetadata(eq(token), eq(awsAccountId), eq(iamServiceAccountName), eq(accessKeyId), Mockito.any())).thenReturn(responseNoContent);
		when(iamServiceAccountUtils.updateActivatedStatusInMetadata(token, iamServiceAccountName, awsAccountId)).thenReturn(getMockResponse(HttpStatus.INTERNAL_SERVER_ERROR, false, ""));

		ResponseEntity<String> expectedResponse =  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"errors\":[\"Failed to activate IAM Service account. IAM secrets are rotated and saved in T-Vault. However metadata update failed.\"]}");
		ResponseEntity<String> actualResponse = iamServiceAccountsService.activateIAMServiceAccount(token, userDetails, iamServiceAccountName, awsAccountId);
		assertEquals(expectedResponse, actualResponse);
	}

	@Test
	public void test_rotateIAMServiceAccount_successfull() {

		String iamServiceAccountName = "svc_vault_test5";
		String token = "123123123123";
		String awsAccountId = "1234567890";
		String path = "metadata/iamsvcacc/1234567890_svc_vault_test5";
		String iamSecret = "abcdefgh";
		String accessKeyId = "testaccesskey";
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");
		String iamMetaDataStr = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		String iamMetaDataStrActivated = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": true, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";


		// Mock approle permission check
		Response lookupResponse = getMockResponse(HttpStatus.OK, true, "{\"policies\":[\"w_iamsvcacc_1234567890_svc_vault_test5 \"]}");
		when(reqProcessor.process("/auth/tvault/lookup","{}", token)).thenReturn(lookupResponse);
		List<String> currentPolicies = new ArrayList<>();
		currentPolicies.add("w_iamsvcacc_1234567890_svc_vault_test5");
		try {
			when(iamServiceAccountUtils.getTokenPoliciesAsListFromTokenLookupJson(Mockito.any(),Mockito.any())).thenReturn(currentPolicies);
			when(iamServiceAccountUtils.getIdentityPoliciesAsListFromTokenLookupJson(Mockito.any(),Mockito.any())).thenReturn(new ArrayList<>());
		} catch (IOException e) {
			e.printStackTrace();
		}

		when(tokenUtils.getSelfServiceToken()).thenReturn(token);

		when(reqProcessor.process("/read", "{\"path\":\""+path+"\"}", token)).thenReturn(getMockResponse(HttpStatus.OK, true,
				iamMetaDataStr));

		IAMServiceAccountSecret iamServiceAccountSecret = new IAMServiceAccountSecret(iamServiceAccountName, accessKeyId, iamSecret, 1609754282000L, awsAccountId);

		when(iamServiceAccountUtils.rotateIAMSecret(Mockito.any())).thenReturn(iamServiceAccountSecret);
		when(iamServiceAccountUtils.writeIAMSvcAccSecret(token, "iamsvcacc/1234567890_svc_vault_test5/secret_1", iamServiceAccountName, iamServiceAccountSecret)).thenReturn(true);
		when(iamServiceAccountUtils.updateIAMSvcAccNewAccessKeyIdInMetadata(eq(token), eq(awsAccountId), eq(iamServiceAccountName), eq(accessKeyId), Mockito.any())).thenReturn(responseNoContent);
		when(iamServiceAccountUtils.updateActivatedStatusInMetadata(token, iamServiceAccountName, awsAccountId)).thenReturn(responseNoContent);

		ResponseEntity<String> expectedResponse =  ResponseEntity.status(HttpStatus.OK).body("{\"messages\":[\"IAM Service account secret rotated successfully\"]}");
		IAMServiceAccountRotateRequest iamServiceAccountRotateRequest = new IAMServiceAccountRotateRequest(accessKeyId, iamServiceAccountName, awsAccountId);
		ResponseEntity<String> actualResponse = iamServiceAccountsService.rotateIAMServiceAccount(token, iamServiceAccountRotateRequest);
		assertEquals(expectedResponse, actualResponse);
	}

	@Test
	public void test_rotateIAMServiceAccount_failed_403() {

		String iamServiceAccountName = "svc_vault_test5";
		String token = "123123123123";
		String awsAccountId = "1234567890";
		String accessKeyId = "testaccesskey";

		// Mock approle permission check
		Response lookupResponse = getMockResponse(HttpStatus.OK, true, "{\"policies\":[\"w_iamsvcacc_1234567890_svc_vault_test1 \"]}");
		when(reqProcessor.process("/auth/tvault/lookup","{}", token)).thenReturn(lookupResponse);
		List<String> currentPolicies = new ArrayList<>();
		currentPolicies.add("w_iamsvcacc_1234567890_svc_vault_test1");
		try {
			when(iamServiceAccountUtils.getTokenPoliciesAsListFromTokenLookupJson(Mockito.any(),Mockito.any())).thenReturn(currentPolicies);
			when(iamServiceAccountUtils.getIdentityPoliciesAsListFromTokenLookupJson(Mockito.any(),Mockito.any())).thenReturn(new ArrayList<>());
		} catch (IOException e) {
			e.printStackTrace();
		}

		ResponseEntity<String> expectedResponse =  ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"errors\":[\"Access denied: No permission to rotate secret for IAM service account.\"]}");
		IAMServiceAccountRotateRequest iamServiceAccountRotateRequest = new IAMServiceAccountRotateRequest(accessKeyId, iamServiceAccountName, awsAccountId);
		ResponseEntity<String> actualResponse = iamServiceAccountsService.rotateIAMServiceAccount(token, iamServiceAccountRotateRequest);
		assertEquals(expectedResponse, actualResponse);
	}

	@Test
	public void test_rotateIAMServiceAccount_faile_to_rotate_secret() {

		String iamServiceAccountName = "svc_vault_test5";
		String token = "123123123123";
		String awsAccountId = "1234567890";
		String path = "metadata/iamsvcacc/1234567890_svc_vault_test5";
		String iamSecret = "abcdefgh";
		String accessKeyId = "testaccesskey";
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");
		String iamMetaDataStr = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		String iamMetaDataStrActivated = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": true, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";


		// Mock approle permission check
		Response lookupResponse = getMockResponse(HttpStatus.OK, true, "{\"policies\":[\"w_iamsvcacc_1234567890_svc_vault_test5 \"]}");
		when(reqProcessor.process("/auth/tvault/lookup","{}", token)).thenReturn(lookupResponse);
		List<String> currentPolicies = new ArrayList<>();
		currentPolicies.add("w_iamsvcacc_1234567890_svc_vault_test5");
		try {
			when(iamServiceAccountUtils.getTokenPoliciesAsListFromTokenLookupJson(Mockito.any(),Mockito.any())).thenReturn(currentPolicies);
			when(iamServiceAccountUtils.getIdentityPoliciesAsListFromTokenLookupJson(Mockito.any(),Mockito.any())).thenReturn(new ArrayList<>());
		} catch (IOException e) {
			e.printStackTrace();
		}

		when(tokenUtils.getSelfServiceToken()).thenReturn(token);

		when(reqProcessor.process("/read", "{\"path\":\""+path+"\"}", token)).thenReturn(getMockResponse(HttpStatus.OK, true,
				iamMetaDataStr));

		IAMServiceAccountSecret iamServiceAccountSecret = new IAMServiceAccountSecret(iamServiceAccountName, accessKeyId, iamSecret, 1609754282000L, awsAccountId);

		when(iamServiceAccountUtils.rotateIAMSecret(Mockito.any())).thenReturn(null);
		when(iamServiceAccountUtils.writeIAMSvcAccSecret(token, "iamsvcacc/1234567890_svc_vault_test5/secret_1", iamServiceAccountName, iamServiceAccountSecret)).thenReturn(true);
		when(iamServiceAccountUtils.updateIAMSvcAccNewAccessKeyIdInMetadata(eq(token), eq(awsAccountId), eq(iamServiceAccountName), eq(accessKeyId), Mockito.any())).thenReturn(responseNoContent);
		when(iamServiceAccountUtils.updateActivatedStatusInMetadata(token, iamServiceAccountName, awsAccountId)).thenReturn(responseNoContent);

		ResponseEntity<String> expectedResponse =  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"errors\":[\"Failed to rotate secret for IAM Service account Access Key Id\"]}");
		IAMServiceAccountRotateRequest iamServiceAccountRotateRequest = new IAMServiceAccountRotateRequest(accessKeyId, iamServiceAccountName, awsAccountId);
		ResponseEntity<String> actualResponse = iamServiceAccountsService.rotateIAMServiceAccount(token, iamServiceAccountRotateRequest);
		assertEquals(expectedResponse, actualResponse);
	}

	@Test
	public void test_addUserToIAMServiceAccount_successfull() {

		String iamServiceAccountName = "svc_vault_test5";
		String token = "123123123123";
		String awsAccountId = "1234567890";
		String path = "metadata/iamsvcacc/1234567890_svc_vault_test5";
		String iamSecret = "abcdefgh";
		String accessKeyId = "testaccesskey";
		String [] policies = {"o_iamsvcacc_1234567890_svc_vault_test5"};
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");
		String iamMetaDataStr = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		String iamMetaDataStrActivated = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": true, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";

		Response metaResponse = getMockResponse(HttpStatus.OK, true, iamMetaDataStr);
		Response metaActivatedResponse = getMockResponse(HttpStatus.OK, true, iamMetaDataStrActivated);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);

		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(metaActivatedResponse);

		when(reqProcessor.process("/read", "{\"path\":\""+path+"\"}", token)).thenReturn(getMockResponse(HttpStatus.OK, true,
				iamMetaDataStr));

		IAMServiceAccountSecret iamServiceAccountSecret = new IAMServiceAccountSecret(iamServiceAccountName, accessKeyId, iamSecret, 1609754282000L, awsAccountId);

		when(iamServiceAccountUtils.rotateIAMSecret(Mockito.any())).thenReturn(iamServiceAccountSecret);
		when(iamServiceAccountUtils.writeIAMSvcAccSecret(token, "iamsvcacc/1234567890_svc_vault_test5/secret_1", iamServiceAccountName, iamServiceAccountSecret)).thenReturn(true);
		when(iamServiceAccountUtils.updateIAMSvcAccNewAccessKeyIdInMetadata(eq(token), eq(awsAccountId), eq(iamServiceAccountName), eq(accessKeyId), Mockito.any())).thenReturn(responseNoContent);
		when(iamServiceAccountUtils.updateActivatedStatusInMetadata(token, iamServiceAccountName, awsAccountId)).thenReturn(responseNoContent);


		// Add User to Service Account
		Response userResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		Response ldapConfigureResponse = getMockResponse(HttpStatus.NO_CONTENT, true, "{\"policies\":null}");
		when(reqProcessor.process("/auth/ldap/users", "{\"username\":\"normaluser\"}", token)).thenReturn(userResponse);

		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			when(ControllerUtil.getPoliciesAsListFromJson(any(), any())).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPUser(eq("normaluser"), any(), any(), eq(token)))
				.thenReturn(ldapConfigureResponse);
		when(ControllerUtil.updateMetadata(any(), any())).thenReturn(responseNoContent);

		ResponseEntity<String> expectedResponse =  ResponseEntity.status(HttpStatus.OK).body("{\"messages\":[\"Successfully added user to the IAM Service Account\"]}");
		IAMServiceAccountUser iamServiceAccountUser =  new IAMServiceAccountUser(iamServiceAccountName, "normaluser", "read",awsAccountId);
		ResponseEntity<String> actualResponse = iamServiceAccountsService.addUserToIAMServiceAccount(token, userDetails, iamServiceAccountUser, false);
		assertEquals(expectedResponse, actualResponse);
	}

	@Test
	public void test_addUserToIAMServiceAccount_successfull_oidc() {

		String iamServiceAccountName = "svc_vault_test5";
		String token = "123123123123";
		String awsAccountId = "1234567890";
		String path = "metadata/iamsvcacc/1234567890_svc_vault_test5";
		String iamSecret = "abcdefgh";
		String accessKeyId = "testaccesskey";
		String [] policies = {"o_iamsvcacc_1234567890_svc_vault_test5"};
		Response responseNoContent = getMockResponse(HttpStatus.NO_CONTENT, true, "");
		String iamMetaDataStr = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		String iamMetaDataStrActivated = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": true, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";

		ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "oidc");

		Response metaActivatedResponse = getMockResponse(HttpStatus.OK, true, iamMetaDataStrActivated);
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		when(policyUtils.getCurrentPolicies(token, userDetails.getUsername(), userDetails)).thenReturn(policies);

		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(metaActivatedResponse);

		when(reqProcessor.process("/read", "{\"path\":\""+path+"\"}", token)).thenReturn(getMockResponse(HttpStatus.OK, true,
				iamMetaDataStr));

		IAMServiceAccountSecret iamServiceAccountSecret = new IAMServiceAccountSecret(iamServiceAccountName, accessKeyId, iamSecret, 1609754282000L, awsAccountId);

		when(iamServiceAccountUtils.rotateIAMSecret(Mockito.any())).thenReturn(iamServiceAccountSecret);
		when(iamServiceAccountUtils.writeIAMSvcAccSecret(token, "iamsvcacc/1234567890_svc_vault_test5/secret_1", iamServiceAccountName, iamServiceAccountSecret)).thenReturn(true);
		when(iamServiceAccountUtils.updateIAMSvcAccNewAccessKeyIdInMetadata(eq(token), eq(awsAccountId), eq(iamServiceAccountName), eq(accessKeyId), Mockito.any())).thenReturn(responseNoContent);
		when(iamServiceAccountUtils.updateActivatedStatusInMetadata(token, iamServiceAccountName, awsAccountId)).thenReturn(responseNoContent);

		// oidc mock
		OIDCEntityResponse oidcEntityResponse = new OIDCEntityResponse();
		oidcEntityResponse.setEntityName("entity");
		List<String> policies1 = new ArrayList<>();
		policies1.add("safeadmin");
		oidcEntityResponse.setPolicies(policies1);
		ResponseEntity<OIDCEntityResponse> responseEntity2 = ResponseEntity.status(HttpStatus.OK)
				.body(oidcEntityResponse);
		when(OIDCUtil.oidcFetchEntityDetails(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(responseEntity2);

		when(OIDCUtil.updateOIDCEntity(Mockito.any(), Mockito.any())).thenReturn(getMockResponse(HttpStatus.NO_CONTENT, true, ""));
		when(ControllerUtil.updateMetadata(any(), any())).thenReturn(responseNoContent);

		ResponseEntity<String> expectedResponse =  ResponseEntity.status(HttpStatus.OK).body("{\"messages\":[\"Successfully added user to the IAM Service Account\"]}");
		IAMServiceAccountUser iamServiceAccountUser =  new IAMServiceAccountUser(iamServiceAccountName, "normaluser", "read",awsAccountId);
		ResponseEntity<String> actualResponse = iamServiceAccountsService.addUserToIAMServiceAccount(token, userDetails, iamServiceAccountUser, false);
		assertEquals(expectedResponse, actualResponse);
	}

	@Test
	public void testoffboardIAMServiceAccountLdap_succss() {
		userDetails = getMockUser(true);
		token = userDetails.getClientToken();
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "ldap");
		IAMServiceAccountOffboardRequest serviceAccount = new IAMServiceAccountOffboardRequest("testaccount", "1234567");
		String iamSvcAccName = serviceAccount.getAwsAccountId() + "_" + serviceAccount.getIamSvcAccName();
		String iamSvccAccPath = IAMServiceAccountConstants.IAM_SVCC_ACC_PATH + iamSvcAccName;

		// Mock approle permission check
		Response lookupResponse = getMockResponse(HttpStatus.OK, true, "{\"policies\":[\"iamportal_master_policy \"]}");
		when(reqProcessor.process("/auth/tvault/lookup","{}", token)).thenReturn(lookupResponse);
		List<String> currentPolicies = new ArrayList<>();
		currentPolicies.add("iamportal_master_policy");
		try {
			when(iamServiceAccountUtils.getTokenPoliciesAsListFromTokenLookupJson(Mockito.any(),Mockito.any())).thenReturn(currentPolicies);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// oidc mock
		OIDCEntityResponse oidcEntityResponse = new OIDCEntityResponse();
		oidcEntityResponse.setEntityName("entity");
		List<String> policies = new ArrayList<>();
		policies.add("safeadmin");
		oidcEntityResponse.setPolicies(policies);
		ResponseEntity<OIDCEntityResponse> oidcResponse = ResponseEntity.status(HttpStatus.OK).body(oidcEntityResponse);
		when(OIDCUtil.oidcFetchEntityDetails(any(), any(), any())).thenReturn(oidcResponse);

		// delete policy mock
		ResponseEntity<String> deletePolicyResponse = ResponseEntity.status(HttpStatus.OK)
				.body("{\"messages\":[\"Successfully created policies for IAM service account\"]}");
		when(accessService.deletePolicyInfo(Mockito.anyString(), Mockito.any())).thenReturn(deletePolicyResponse);

		// metadata mock
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"owner_ntid\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"},\"groups\":{\"testgroup1\":\"read\"},\"app-roles\":{\"approle1\":\"read\"}}}"));

		// Mock user response and config user
		Response userResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		Response ldapConfigureResponse = getMockResponse(HttpStatus.NO_CONTENT, true, "{\"policies\":null}");
		when(reqProcessor.process("/auth/ldap/users", "{\"username\":\"normaluser\"}", token)).thenReturn(userResponse);

		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			when(ControllerUtil.getPoliciesAsListFromJson(any(), any())).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPUser(eq("normaluser"), any(), any(), eq(token)))
				.thenReturn(ldapConfigureResponse);

		// Mock group response and config group
		Response groupResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"testgroup1\"}", token)).thenReturn(groupResponse);
		when(ControllerUtil.configureLDAPGroup(eq("testgroup1"), any(), eq(token))).thenReturn(ldapConfigureResponse);

		// Mock approle response and config approle
		Response approleResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/approle/role/read","{\"role_name\":\"approle1\"}", token)).thenReturn(approleResponse);
		when(appRoleService.configureApprole(eq("approle1"), any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true, ""));

		// System under test
		String expectedResponse = "{\"messages\":[\"Successfully offboarded IAM service account (if existed) from T-Vault\"]}";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK).body(expectedResponse);

		String iamMetaDataStr = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		when(reqProcessor.process(eq("/read"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				iamMetaDataStr));

		when(reqProcessor.process(eq("/delete"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.NO_CONTENT, true,
				""));

		ResponseEntity<String> responseEntity = iamServiceAccountsService.offboardIAMServiceAccount(token,
				serviceAccount, userDetails);
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}


	@Test
	public void testoffboardIAMServiceAccountOIDC_succss() {
		userDetails = getMockUser(true);
		token = userDetails.getClientToken();
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "oidc");
		IAMServiceAccountOffboardRequest serviceAccount = new IAMServiceAccountOffboardRequest("testaccount", "1234567");
		String iamSvcAccName = serviceAccount.getAwsAccountId() + "_" + serviceAccount.getIamSvcAccName();

		// Mock approle permission check
		Response lookupResponse = getMockResponse(HttpStatus.OK, true, "{\"policies\":[\"iamportal_master_policy \"]}");
		when(reqProcessor.process("/auth/tvault/lookup","{}", token)).thenReturn(lookupResponse);
		List<String> currentPolicies = new ArrayList<>();
		currentPolicies.add("iamportal_master_policy");
		try {
			when(iamServiceAccountUtils.getTokenPoliciesAsListFromTokenLookupJson(Mockito.any(),Mockito.any())).thenReturn(currentPolicies);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// oidc mock
		OIDCEntityResponse oidcEntityResponse = new OIDCEntityResponse();
		oidcEntityResponse.setEntityName("entity");
		List<String> policies = new ArrayList<>();
		policies.add("safeadmin");
		oidcEntityResponse.setPolicies(policies);
		ResponseEntity<OIDCEntityResponse> oidcResponse = ResponseEntity.status(HttpStatus.OK).body(oidcEntityResponse);
		when(OIDCUtil.oidcFetchEntityDetails(any(), any(), any())).thenReturn(oidcResponse);

		// delete policy mock
		ResponseEntity<String> deletePolicyResponse = ResponseEntity.status(HttpStatus.OK)
				.body("{\"messages\":[\"Successfully created policies for IAM service account\"]}");
		when(accessService.deletePolicyInfo(Mockito.anyString(), Mockito.any())).thenReturn(deletePolicyResponse);

		// metadata mock
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"owner_ntid\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"},\"groups\":{\"testgroup1\":\"read\"},\"app-roles\":{\"approle1\":\"read\"}}}"));

		// Mock user response and config user
		Response userResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		Response ldapConfigureResponse = getMockResponse(HttpStatus.NO_CONTENT, true, "{\"policies\":null}");
		when(reqProcessor.process("/auth/ldap/users", "{\"username\":\"normaluser\"}", token)).thenReturn(userResponse);

		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			when(ControllerUtil.getPoliciesAsListFromJson(any(), any())).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPUser(eq("normaluser"), any(), any(), eq(token)))
				.thenReturn(ldapConfigureResponse);

		// Mock group response and config group
		List<String> currentpolicies = new ArrayList<>();
		currentpolicies.add("default");
		currentpolicies.add("w_shared_mysafe01");
		currentpolicies.add("w_shared_mysafe02");
		OIDCGroup oidcGroup = new OIDCGroup("123-123-123", currentpolicies);
		when(OIDCUtil.getIdentityGroupDetails("testgroup1", token)).thenReturn(oidcGroup);
		Response groupResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"testgroup1\"}", token)).thenReturn(groupResponse);
		when(ControllerUtil.configureLDAPGroup(eq("testgroup1"), any(), eq(token))).thenReturn(ldapConfigureResponse);

		// Mock approle response and config approle
		Response approleResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/approle/role/read","{\"role_name\":\"approle1\"}", token)).thenReturn(approleResponse);
		when(appRoleService.configureApprole(eq("approle1"), any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true, ""));

		// System under test
		String expectedResponse = "{\"messages\":[\"Successfully offboarded IAM service account (if existed) from T-Vault\"]}";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK).body(expectedResponse);

		String iamMetaDataStr = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		when(reqProcessor.process(eq("/read"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				iamMetaDataStr));

		when(reqProcessor.process(eq("/delete"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.NO_CONTENT, true,
				""));

		ResponseEntity<String> responseEntity = iamServiceAccountsService.offboardIAMServiceAccount(token,
				serviceAccount, userDetails);
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testoffboardIAMServiceAccountOIDC_failed_403() {
		userDetails = getMockUser(true);
		token = userDetails.getClientToken();
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "oidc");
		IAMServiceAccountOffboardRequest serviceAccount = new IAMServiceAccountOffboardRequest("testaccount", "1234567");
		String iamSvcAccName = serviceAccount.getAwsAccountId() + "_" + serviceAccount.getIamSvcAccName();

		// Mock approle permission check
		Response lookupResponse = getMockResponse(HttpStatus.OK, true, "{\"policies\":[\"iamportal_master_policy \"]}");
		when(reqProcessor.process("/auth/tvault/lookup","{}", token)).thenReturn(lookupResponse);
		List<String> currentPolicies = new ArrayList<>();
		currentPolicies.add("default");
		try {
			when(iamServiceAccountUtils.getTokenPoliciesAsListFromTokenLookupJson(Mockito.any(),Mockito.any())).thenReturn(currentPolicies);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// System under test
		String expectedResponse = "{\"errors\":[\"Access denied. Not authorized to perform offboarding of IAM service accounts.\"]}";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.FORBIDDEN).body(expectedResponse);

		ResponseEntity<String> responseEntity = iamServiceAccountsService.offboardIAMServiceAccount(token,
				serviceAccount, userDetails);
		assertEquals(HttpStatus.FORBIDDEN, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testoffboardIAMServiceAccountOIDC_failed_to_delete_policy() {
		userDetails = getMockUser(true);
		token = userDetails.getClientToken();
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "oidc");
		IAMServiceAccountOffboardRequest serviceAccount = new IAMServiceAccountOffboardRequest("testaccount", "1234567");
		String iamSvcAccName = serviceAccount.getAwsAccountId() + "_" + serviceAccount.getIamSvcAccName();

		// Mock approle permission check
		Response lookupResponse = getMockResponse(HttpStatus.OK, true, "{\"policies\":[\"iamportal_master_policy \"]}");
		when(reqProcessor.process("/auth/tvault/lookup","{}", token)).thenReturn(lookupResponse);
		List<String> currentPolicies = new ArrayList<>();
		currentPolicies.add("iamportal_master_policy");
		try {
			when(iamServiceAccountUtils.getTokenPoliciesAsListFromTokenLookupJson(Mockito.any(),Mockito.any())).thenReturn(currentPolicies);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// oidc mock
		OIDCEntityResponse oidcEntityResponse = new OIDCEntityResponse();
		oidcEntityResponse.setEntityName("entity");
		List<String> policies = new ArrayList<>();
		policies.add("safeadmin");
		oidcEntityResponse.setPolicies(policies);
		ResponseEntity<OIDCEntityResponse> oidcResponse = ResponseEntity.status(HttpStatus.OK).body(oidcEntityResponse);
		when(OIDCUtil.oidcFetchEntityDetails(any(), any(), any())).thenReturn(oidcResponse);

		// delete policy mock
		ResponseEntity<String> deletePolicyResponse = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("");
		when(accessService.deletePolicyInfo(Mockito.anyString(), Mockito.any())).thenReturn(deletePolicyResponse);

		// System under test
		String expectedResponse = "{\"errors\":[\"Failed to Offboard IAM service account. Policy deletion failed.\"]}";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(expectedResponse);

		ResponseEntity<String> responseEntity = iamServiceAccountsService.offboardIAMServiceAccount(token,
				serviceAccount, userDetails);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testoffboardIAMServiceAccountOIDC_failed_to_delete_secret() {
		userDetails = getMockUser(true);
		token = userDetails.getClientToken();
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "oidc");
		IAMServiceAccountOffboardRequest serviceAccount = new IAMServiceAccountOffboardRequest("testaccount", "1234567");
		String iamSvcAccName = serviceAccount.getAwsAccountId() + "_" + serviceAccount.getIamSvcAccName();

		// Mock approle permission check
		Response lookupResponse = getMockResponse(HttpStatus.OK, true, "{\"policies\":[\"iamportal_master_policy \"]}");
		when(reqProcessor.process("/auth/tvault/lookup","{}", token)).thenReturn(lookupResponse);
		List<String> currentPolicies = new ArrayList<>();
		currentPolicies.add("iamportal_master_policy");
		try {
			when(iamServiceAccountUtils.getTokenPoliciesAsListFromTokenLookupJson(Mockito.any(),Mockito.any())).thenReturn(currentPolicies);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// oidc mock
		OIDCEntityResponse oidcEntityResponse = new OIDCEntityResponse();
		oidcEntityResponse.setEntityName("entity");
		List<String> policies = new ArrayList<>();
		policies.add("safeadmin");
		oidcEntityResponse.setPolicies(policies);
		ResponseEntity<OIDCEntityResponse> oidcResponse = ResponseEntity.status(HttpStatus.OK).body(oidcEntityResponse);
		when(OIDCUtil.oidcFetchEntityDetails(any(), any(), any())).thenReturn(oidcResponse);

		// delete policy mock
		ResponseEntity<String> deletePolicyResponse = ResponseEntity.status(HttpStatus.OK)
				.body("{\"messages\":[\"Successfully created policies for IAM service account\"]}");
		when(accessService.deletePolicyInfo(Mockito.anyString(), Mockito.any())).thenReturn(deletePolicyResponse);

		// metadata mock
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"owner_ntid\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"},\"groups\":{\"testgroup1\":\"read\"},\"app-roles\":{\"approle1\":\"read\"}}}"));

		// Mock user response and config user
		Response userResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		Response ldapConfigureResponse = getMockResponse(HttpStatus.NO_CONTENT, true, "{\"policies\":null}");
		when(reqProcessor.process("/auth/ldap/users", "{\"username\":\"normaluser\"}", token)).thenReturn(userResponse);

		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			when(ControllerUtil.getPoliciesAsListFromJson(any(), any())).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPUser(eq("normaluser"), any(), any(), eq(token)))
				.thenReturn(ldapConfigureResponse);

		// Mock group response and config group
		List<String> currentpolicies = new ArrayList<>();
		currentpolicies.add("default");
		currentpolicies.add("w_shared_mysafe01");
		currentpolicies.add("w_shared_mysafe02");
		OIDCGroup oidcGroup = new OIDCGroup("123-123-123", currentpolicies);
		when(OIDCUtil.getIdentityGroupDetails("testgroup1", token)).thenReturn(oidcGroup);
		Response groupResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"testgroup1\"}", token)).thenReturn(groupResponse);
		when(ControllerUtil.configureLDAPGroup(eq("testgroup1"), any(), eq(token))).thenReturn(ldapConfigureResponse);

		// Mock approle response and config approle
		Response approleResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/approle/role/read","{\"role_name\":\"approle1\"}", token)).thenReturn(approleResponse);
		when(appRoleService.configureApprole(eq("approle1"), any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true, ""));

		// System under test
		String expectedResponse = "{\"errors\":[\"Failed to offboard IAM service account from TVault\"]}";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.MULTI_STATUS).body(expectedResponse);

		String iamMetaDataStr = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		when(reqProcessor.process(eq("/read"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				iamMetaDataStr));

		when(reqProcessor.process(eq("/delete"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.MULTI_STATUS, true,
				""));

		ResponseEntity<String> responseEntity = iamServiceAccountsService.offboardIAMServiceAccount(token,
				serviceAccount, userDetails);
		assertEquals(HttpStatus.MULTI_STATUS, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testoffboardIAMServiceAccountOIDC_failed_to_delete_folder() {
		userDetails = getMockUser(true);
		token = userDetails.getClientToken();
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "oidc");
		IAMServiceAccountOffboardRequest serviceAccount = new IAMServiceAccountOffboardRequest("testaccount", "1234567");
		String iamSvcAccName = serviceAccount.getAwsAccountId() + "_" + serviceAccount.getIamSvcAccName();

		// Mock approle permission check
		Response lookupResponse = getMockResponse(HttpStatus.OK, true, "{\"policies\":[\"iamportal_master_policy \"]}");
		when(reqProcessor.process("/auth/tvault/lookup","{}", token)).thenReturn(lookupResponse);
		List<String> currentPolicies = new ArrayList<>();
		currentPolicies.add("iamportal_master_policy");
		try {
			when(iamServiceAccountUtils.getTokenPoliciesAsListFromTokenLookupJson(Mockito.any(),Mockito.any())).thenReturn(currentPolicies);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// oidc mock
		OIDCEntityResponse oidcEntityResponse = new OIDCEntityResponse();
		oidcEntityResponse.setEntityName("entity");
		List<String> policies = new ArrayList<>();
		policies.add("safeadmin");
		oidcEntityResponse.setPolicies(policies);
		ResponseEntity<OIDCEntityResponse> oidcResponse = ResponseEntity.status(HttpStatus.OK).body(oidcEntityResponse);
		when(OIDCUtil.oidcFetchEntityDetails(any(), any(), any())).thenReturn(oidcResponse);

		// delete policy mock
		ResponseEntity<String> deletePolicyResponse = ResponseEntity.status(HttpStatus.OK)
				.body("{\"messages\":[\"Successfully created policies for IAM service account\"]}");
		when(accessService.deletePolicyInfo(Mockito.anyString(), Mockito.any())).thenReturn(deletePolicyResponse);

		// metadata mock
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"owner_ntid\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"},\"groups\":{\"testgroup1\":\"read\"},\"app-roles\":{\"approle1\":\"read\"}}}"));

		// Mock user response and config user
		Response userResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		Response ldapConfigureResponse = getMockResponse(HttpStatus.NO_CONTENT, true, "{\"policies\":null}");
		when(reqProcessor.process("/auth/ldap/users", "{\"username\":\"normaluser\"}", token)).thenReturn(userResponse);

		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			when(ControllerUtil.getPoliciesAsListFromJson(any(), any())).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPUser(eq("normaluser"), any(), any(), eq(token)))
				.thenReturn(ldapConfigureResponse);

		// Mock group response and config group
		List<String> currentpolicies = new ArrayList<>();
		currentpolicies.add("default");
		currentpolicies.add("w_shared_mysafe01");
		currentpolicies.add("w_shared_mysafe02");
		OIDCGroup oidcGroup = new OIDCGroup("123-123-123", currentpolicies);
		when(OIDCUtil.getIdentityGroupDetails("testgroup1", token)).thenReturn(oidcGroup);
		Response groupResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"testgroup1\"}", token)).thenReturn(groupResponse);
		when(ControllerUtil.configureLDAPGroup(eq("testgroup1"), any(), eq(token))).thenReturn(ldapConfigureResponse);

		// Mock approle response and config approle
		Response approleResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/approle/role/read","{\"role_name\":\"approle1\"}", token)).thenReturn(approleResponse);
		when(appRoleService.configureApprole(eq("approle1"), any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true, ""));

		// System under test
		String expectedResponse = "{\"errors\":[\"Failed to offboard IAM service account from TVault\"]}";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.MULTI_STATUS).body(expectedResponse);

		String iamMetaDataStr = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		when(reqProcessor.process(eq("/read"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				iamMetaDataStr));

		when(reqProcessor.process(eq("/delete"), Mockito.any(), eq(token))).thenAnswer(new Answer() {
			private int count = 0;

			public Object answer(InvocationOnMock invocation) {
				if (count++ == 2)
					return getMockResponse(HttpStatus.MULTI_STATUS, true,"");

				return getMockResponse(HttpStatus.NO_CONTENT, true,"");
			}
		});

		ResponseEntity<String> responseEntity = iamServiceAccountsService.offboardIAMServiceAccount(token,
				serviceAccount, userDetails);
		assertEquals(HttpStatus.MULTI_STATUS, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

	@Test
	public void testoffboardIAMServiceAccountOIDC_failed_to_delete_metadata() {
		userDetails = getMockUser(true);
		token = userDetails.getClientToken();
		when(tokenUtils.getSelfServiceToken()).thenReturn(token);
		ReflectionTestUtils.setField(iamServiceAccountsService, "vaultAuthMethod", "oidc");
		IAMServiceAccountOffboardRequest serviceAccount = new IAMServiceAccountOffboardRequest("testaccount", "1234567");
		String iamSvcAccName = serviceAccount.getAwsAccountId() + "_" + serviceAccount.getIamSvcAccName();

		// Mock approle permission check
		Response lookupResponse = getMockResponse(HttpStatus.OK, true, "{\"policies\":[\"iamportal_master_policy \"]}");
		when(reqProcessor.process("/auth/tvault/lookup","{}", token)).thenReturn(lookupResponse);
		List<String> currentPolicies = new ArrayList<>();
		currentPolicies.add("iamportal_master_policy");
		try {
			when(iamServiceAccountUtils.getTokenPoliciesAsListFromTokenLookupJson(Mockito.any(),Mockito.any())).thenReturn(currentPolicies);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// oidc mock
		OIDCEntityResponse oidcEntityResponse = new OIDCEntityResponse();
		oidcEntityResponse.setEntityName("entity");
		List<String> policies = new ArrayList<>();
		policies.add("safeadmin");
		oidcEntityResponse.setPolicies(policies);
		ResponseEntity<OIDCEntityResponse> oidcResponse = ResponseEntity.status(HttpStatus.OK).body(oidcEntityResponse);
		when(OIDCUtil.oidcFetchEntityDetails(any(), any(), any())).thenReturn(oidcResponse);

		// delete policy mock
		ResponseEntity<String> deletePolicyResponse = ResponseEntity.status(HttpStatus.OK)
				.body("{\"messages\":[\"Successfully created policies for IAM service account\"]}");
		when(accessService.deletePolicyInfo(Mockito.anyString(), Mockito.any())).thenReturn(deletePolicyResponse);

		// metadata mock
		when(reqProcessor.process(eq("/sdb"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"isActivated\":true,\"owner_ntid\":\"normaluser\",\"name\":\"svc_vault_test5\",\"users\":{\"normaluser\":\"sudo\"},\"groups\":{\"testgroup1\":\"read\"},\"app-roles\":{\"approle1\":\"read\"}}}"));

		// Mock user response and config user
		Response userResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		Response ldapConfigureResponse = getMockResponse(HttpStatus.NO_CONTENT, true, "{\"policies\":null}");
		when(reqProcessor.process("/auth/ldap/users", "{\"username\":\"normaluser\"}", token)).thenReturn(userResponse);

		try {
			List<String> resList = new ArrayList<>();
			resList.add("default");
			when(ControllerUtil.getPoliciesAsListFromJson(any(), any())).thenReturn(resList);
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(ControllerUtil.configureLDAPUser(eq("normaluser"), any(), any(), eq(token)))
				.thenReturn(ldapConfigureResponse);

		// Mock group response and config group
		List<String> currentpolicies = new ArrayList<>();
		currentpolicies.add("default");
		currentpolicies.add("w_shared_mysafe01");
		currentpolicies.add("w_shared_mysafe02");
		OIDCGroup oidcGroup = new OIDCGroup("123-123-123", currentpolicies);
		when(OIDCUtil.getIdentityGroupDetails("testgroup1", token)).thenReturn(oidcGroup);
		Response groupResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/ldap/groups", "{\"groupname\":\"testgroup1\"}", token)).thenReturn(groupResponse);
		when(ControllerUtil.configureLDAPGroup(eq("testgroup1"), any(), eq(token))).thenReturn(ldapConfigureResponse);

		// Mock approle response and config approle
		Response approleResponse = getMockResponse(HttpStatus.OK, true,
				"{\"data\":{\"bound_cidrs\":[],\"max_ttl\":0,\"policies\":[\"default\"],\"ttl\":0,\"groups\":\"admin\"}}");
		when(reqProcessor.process("/auth/approle/role/read","{\"role_name\":\"approle1\"}", token)).thenReturn(approleResponse);
		when(appRoleService.configureApprole(eq("approle1"), any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true, ""));

		// System under test
		String expectedResponse = "{\"errors\":[\"Failed to offboard IAM service account from TVault\"]}";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.MULTI_STATUS).body(expectedResponse);

		String iamMetaDataStr = "{ \"data\": {\"userName\": \"svc_vault_test5\", \"awsAccountId\": \"1234567890\", \"awsAccountName\": \"testaccount1\", \"createdAtEpoch\": 1609754282000, \"owner_ntid\": \"normaluser\", \"owner_email\": \"normaluser@testmail.com\", \"application_id\": \"app1\", \"application_name\": \"App1\", \"application_tag\": \"App1\", \"isActivated\": false, \"secret\":[{\"accessKeyId\":\"testaccesskey\", \"expiryDuration\":12345}]}, \"path\": \"iamsvcacc/1234567890_svc_vault_test5\"}";
		when(reqProcessor.process(eq("/read"), Mockito.any(), eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true,
				iamMetaDataStr));

		when(reqProcessor.process(eq("/delete"), Mockito.any(), eq(token))).thenAnswer(new Answer() {
			private int count = 0;

			public Object answer(InvocationOnMock invocation) {
				if (count++ == 1)
					return getMockResponse(HttpStatus.MULTI_STATUS, true,"");

				return getMockResponse(HttpStatus.NO_CONTENT, true,"");
			}
		});

		ResponseEntity<String> responseEntity = iamServiceAccountsService.offboardIAMServiceAccount(token,
				serviceAccount, userDetails);
		assertEquals(HttpStatus.MULTI_STATUS, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}
	
	@Test
	public void test_readFolders_successfully() throws IOException {
		String token = "5PDrOhsy4ig8L3EpsJZSLAMg";
		String path = "iamsvcacc/123456789012_testiamsvcacc01";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.OK).body(
				"{\"folders\":[\"testiamsvcacc01_01\",\"testiamsvcacc01_02\"],\"path\":\"123456789012_testiamsvcacc01\",\"iamsvcaccName\":\"testiamsvcacc01\"}");

		when(reqProcessor.process(eq("/iam/list"),Mockito.any(),eq(token))).thenReturn(getMockResponse(HttpStatus.OK, true, "{\"keys\":[\"testiamsvcacc01_01\",\"testiamsvcacc01_02\"]}"));
		ResponseEntity<String> responseEntity = iamServiceAccountsService.readFolders(token, path);
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}
	
	@Test
	public void test_readFolders_failure() throws IOException {
		String token = "5PDrOhsy4ig8L3EpsJZSLAMg";
		String path = "iamsvcacc/123456789012_testiamsvcacc01";
		ResponseEntity<String> responseEntityExpected = ResponseEntity.status(HttpStatus.FORBIDDEN).body(
				"{\"errors\":[\"Unable to read the given path :" + path + "\"]}");

		when(reqProcessor.process(eq("/iam/list"),Mockito.any(),eq(token))).thenReturn(getMockResponse(HttpStatus.FORBIDDEN, false, "{\"errors\":[\"1 error occurred:\n\t* permission denied\n\n\"]}"));
		ResponseEntity<String> responseEntity = iamServiceAccountsService.readFolders(token, path);
		assertEquals(HttpStatus.FORBIDDEN, responseEntity.getStatusCode());
		assertEquals(responseEntityExpected, responseEntity);
	}

}
