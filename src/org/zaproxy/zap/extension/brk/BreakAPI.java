/*
 /*
 * Zed Attack Proxy (ZAP) and its related class files.
 * 
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0 
 *   
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */
package org.zaproxy.zap.extension.brk;

import net.sf.json.JSONObject;

import org.parosproxy.paros.network.HttpHeader;
import org.parosproxy.paros.network.HttpMalformedHeaderException;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.zap.extension.api.ApiAction;
import org.zaproxy.zap.extension.api.ApiException;
import org.zaproxy.zap.extension.api.ApiImplementor;
import org.zaproxy.zap.extension.api.ApiResponse;
import org.zaproxy.zap.extension.api.ApiResponseElement;
import org.zaproxy.zap.extension.api.ApiView;
import org.zaproxy.zap.extension.httppanel.Message;

public class BreakAPI extends ApiImplementor {


	private static final String PREFIX = "break";

	private static final String ACTION_BREAK = "break";
	private static final String ACTION_BREAK_ON_ID = "breakOnId";
	private static final String ACTION_ADD_HTTP_BREAK_POINT = "addHttpBreakpoint";
	private static final String ACTION_REM_HTTP_BREAK_POINT = "removeHttpBreakpoint";
	private static final String ACTION_CONTINUE = "continue";
	private static final String ACTION_STEP = "step";
	private static final String ACTION_DROP = "drop";
	private static final String ACTION_SET_HTTP_MESSAGE = "setHttpMessage";

	private static final String VIEW_IS_BREAK_ALL = "isBreakAll";
	private static final String VIEW_IS_BREAK_REQUEST = "isBreakRequest";
	private static final String VIEW_IS_BREAK_RESPONSE = "isBreakResponse";
	private static final String VIEW_HTTP_MESSAGE = "httpMessage";

	private static final String PARAM_STRING = "string";
	private static final String PARAM_LOCATION = "location";
	private static final String PARAM_MATCH = "match";
	private static final String PARAM_INVERSE = "inverse";
	private static final String PARAM_IGNORECASE = "ignorecase";
	private static final String PARAM_KEY = "key";
	private static final String PARAM_SCOPE = "scope";
	private static final String PARAM_STATE = "state";
	private static final String PARAM_TYPE = "type";
	private static final String PARAM_HTTP_HEADER = "httpHeader";
	private static final String PARAM_HTTP_BODY = "httpBody";

	private static final String VALUE_TYPE_HTTP_ALL = "http-all";
	private static final String VALUE_TYPE_HTTP_REQUESTS = "http-requests";
	private static final String VALUE_TYPE_HTTP_RESPONSES = "http-responses";
	

	private ExtensionBreak extension = null;

	public BreakAPI(ExtensionBreak ext) {
		extension = ext;
		
		this.addApiView(new ApiView(VIEW_IS_BREAK_ALL));
		this.addApiView(new ApiView(VIEW_IS_BREAK_REQUEST));
		this.addApiView(new ApiView(VIEW_IS_BREAK_RESPONSE));
		this.addApiView(new ApiView(VIEW_HTTP_MESSAGE));
		
		this.addApiAction(new ApiAction(ACTION_BREAK, new String[] {PARAM_TYPE, PARAM_STATE},
				new String[] {PARAM_SCOPE})); // Not currently used but kept for compatibility parposes
		this.addApiAction(new ApiAction(ACTION_SET_HTTP_MESSAGE, 
				new String[] {PARAM_HTTP_HEADER}, new String[] {PARAM_HTTP_BODY}));
		// Disable pending other changes
		//this.addApiAction(new ApiAction(ACTION_BREAK_ON_ID, new String[] {PARAM_KEY, PARAM_STATE}));
		this.addApiAction(new ApiAction(ACTION_CONTINUE));
		this.addApiAction(new ApiAction(ACTION_STEP));
		this.addApiAction(new ApiAction(ACTION_DROP));
		this.addApiAction(new ApiAction(ACTION_ADD_HTTP_BREAK_POINT, 
				new String[] {PARAM_STRING, PARAM_LOCATION, PARAM_MATCH, PARAM_INVERSE, PARAM_IGNORECASE}));
		this.addApiAction(new ApiAction(ACTION_REM_HTTP_BREAK_POINT, 
				new String[] {PARAM_STRING, PARAM_LOCATION, PARAM_MATCH, PARAM_INVERSE, PARAM_IGNORECASE}));
		
	}

	@Override
	public String getPrefix() {
		return PREFIX;
	}

	@Override
	public ApiResponse handleApiAction(String name, JSONObject params) throws ApiException {
		if (ACTION_BREAK.equals(name)) {
			String type = params.getString(PARAM_TYPE).toLowerCase();
			if (type.equals(VALUE_TYPE_HTTP_ALL)) {
				extension.setBreakAllRequests(params.getBoolean(PARAM_STATE));
				extension.setBreakAllResponses(params.getBoolean(PARAM_STATE));
			} else if (type.equals(VALUE_TYPE_HTTP_REQUESTS)) {
				extension.setBreakAllRequests(params.getBoolean(PARAM_STATE));
			} else if (type.equals(VALUE_TYPE_HTTP_RESPONSES)) {
				extension.setBreakAllResponses(params.getBoolean(PARAM_STATE));
			} else {
				throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_TYPE +
						" not in [" + VALUE_TYPE_HTTP_ALL +"," + VALUE_TYPE_HTTP_REQUESTS +"," +
						VALUE_TYPE_HTTP_RESPONSES +"]");
			}
			
		} else if (ACTION_BREAK_ON_ID.equals(name)) {
			extension.setBreakOnId(params.getString(PARAM_KEY), params.getString(PARAM_STATE).toLowerCase().equals("on"));

		} else if (ACTION_CONTINUE.equals(name)) {
			extension.getBreakpointManagementInterface().cont();

		} else if (ACTION_STEP.equals(name)) {
			extension.getBreakpointManagementInterface().step();

		} else if (ACTION_DROP.equals(name)) {
			extension.getBreakpointManagementInterface().drop();

		} else if (ACTION_SET_HTTP_MESSAGE.equals(name)) {
			if (extension.getBreakpointManagementInterface().getMessage() == null) {
				// We've not got an intercepted message
				throw new ApiException(ApiException.Type.DOES_NOT_EXIST);
			}

			String header = params.getString(PARAM_HTTP_HEADER);
			String body = this.getParam(params, PARAM_HTTP_BODY, "");

			if (header.indexOf(HttpHeader.CRLF) < 0) {
				if (header.indexOf("\\n") >= 0) {
					// Makes it easier to use via API UI
					header = header.replace("\\r", "\r").replace("\\n", "\n");
				}
			}

			if (extension.getBreakpointManagementInterface().isRequest()) {
				HttpMessage msg = new HttpMessage();
	
				try {
					msg.setRequestHeader(header);
					msg.setRequestBody(body);
					extension.getBreakpointManagementInterface().setMessage(msg, true);
					extension.getBreakpointManagementInterface().saveMessage(true);
	
				} catch (HttpMalformedHeaderException e) {
					throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, e.getMessage());
				}
			} else {
				Message msg = extension.getBreakpointManagementInterface().getMessage();
				if (msg instanceof HttpMessage) {
					HttpMessage httpMsg = (HttpMessage)msg;
		
					try {
						httpMsg.setResponseHeader(header);
						httpMsg.setResponseBody(body);
						extension.getBreakpointManagementInterface().setMessage(msg, false);
						extension.getBreakpointManagementInterface().saveMessage(false);
		
					} catch (HttpMalformedHeaderException e) {
						throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, e.getMessage());
					}
				}
			}

		} else if (ACTION_ADD_HTTP_BREAK_POINT.equals(name)) {
			try {
				extension.addHttpBreakpoint(
						params.getString(PARAM_STRING), 
						params.getString(PARAM_LOCATION), 
						params.getString(PARAM_MATCH), 
						params.getBoolean(PARAM_INVERSE), 
						params.getBoolean(PARAM_IGNORECASE));
			} catch (Exception e) {
				throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, e.getMessage());
			}

		} else if (ACTION_REM_HTTP_BREAK_POINT.equals(name)) {
			try {
				extension.removeHttpBreakpoint(
						params.getString(PARAM_STRING), 
						params.getString(PARAM_LOCATION), 
						params.getString(PARAM_MATCH), 
						params.getBoolean(PARAM_INVERSE), 
						params.getBoolean(PARAM_IGNORECASE));
			} catch (Exception e) {
				throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, e.getMessage());
			}

		} else {
			throw new ApiException(ApiException.Type.BAD_ACTION);
		}
		return ApiResponseElement.OK;

	}
	

	@Override
	public ApiResponse handleApiView(String name, JSONObject params) throws ApiException {
		if (VIEW_IS_BREAK_ALL.equals(name)) {
			return new ApiResponseElement(name, 
					Boolean.toString(extension.getBreakpointManagementInterface().isBreakAll()));
		} else if (VIEW_IS_BREAK_REQUEST.equals(name)) {
			return new ApiResponseElement(name, 
					Boolean.toString(extension.getBreakpointManagementInterface().isBreakRequest()));
		} else if (VIEW_IS_BREAK_RESPONSE.equals(name)) {
			return new ApiResponseElement(name, 
					Boolean.toString(extension.getBreakpointManagementInterface().isBreakResponse()));
		} else if (VIEW_HTTP_MESSAGE.equals(name)) {
			Message msg = extension.getBreakpointManagementInterface().getMessage();
			if (msg == null ) {
				return new ApiResponseElement(name, "");
			} else if (msg instanceof HttpMessage) {
				HttpMessage httpMsg = (HttpMessage)msg;
				StringBuilder sb = new StringBuilder();
				if (extension.getBreakpointManagementInterface().isRequest()) {
					sb.append(httpMsg.getRequestHeader().toString());
					sb.append(httpMsg.getRequestBody().toString());
				} else {
					sb.append(httpMsg.getResponseHeader().toString());
					sb.append(httpMsg.getResponseBody().toString());
				}
				return new ApiResponseElement(name, sb.toString());
			}
			throw new ApiException(ApiException.Type.BAD_TYPE);
		} else {
			throw new ApiException(ApiException.Type.BAD_VIEW);
		}
	}
}
