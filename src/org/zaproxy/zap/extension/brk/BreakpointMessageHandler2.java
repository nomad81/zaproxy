/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2010 psiinon@gmail.com
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

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.parosproxy.paros.control.Control.Mode;
import org.parosproxy.paros.view.View;
import org.zaproxy.zap.extension.httppanel.Message;


public class BreakpointMessageHandler2 {

    private static final Logger logger = Logger.getLogger(BreakpointMessageHandler2.class);
    
    protected static final java.lang.Object semaphore = new java.lang.Object();
    
    protected final BreakpointManagementInterface breakMgmt;
    
    protected List<BreakpointMessageInterface> enabledBreakpoints;
    
    private List<String> enabledKeyBreakpoints = new ArrayList<>();
    
    public List<String> getEnabledKeyBreakpoints() {
		return enabledKeyBreakpoints;
	}

	public void setEnabledKeyBreakpoints(List<String> enabledKeyBreakpoints) {
		this.enabledKeyBreakpoints = enabledKeyBreakpoints;
	}

	public BreakpointMessageHandler2(BreakpointManagementInterface aBreakPanel) {
        this.breakMgmt = aBreakPanel;
    }
    
    public void setEnabledBreakpoints(List<BreakpointMessageInterface> breakpoints) {
        this.enabledBreakpoints = breakpoints;
    }
    
    /**
     * Do not call if in {@link Mode#safe}.
     * 
     * @param aMessage
     * @param onlyIfInScope
     * @return False if message should be dropped.
     */
    public boolean handleMessageReceivedFromClient(Message aMessage, boolean onlyIfInScope) {
        if ( ! isBreakpoint(aMessage, true, onlyIfInScope)) {
            return true;
        }
        
        // Do this outside of the semaphore loop so that the 'continue' button can apply to all queued break points
        // but be reset when the next break point is hit
        breakMgmt.breakpointHit();

        synchronized(semaphore) {
            if (breakMgmt.isHoldMessage(aMessage)) {
                setBreakDisplay(aMessage, true);
                waitUntilContinue(aMessage, true);
            }
        }
        clearAndDisableRequest();
        return ! breakMgmt.isToBeDropped();
    }
    
    /**
     * Do not call if in {@link Mode#safe}.
     * 
     * @param aMessage
     * @param onlyIfInScope
     * @return False if message should be dropped.
     */
    public boolean handleMessageReceivedFromServer(Message aMessage, boolean onlyIfInScope) {
        if (! isBreakpoint(aMessage, false, onlyIfInScope)) {
            return true;
        }
        
        // Do this outside of the semaphore loop so that the 'continue' button can apply to all queued break points
        // but be reset when the next break point is hit
        breakMgmt.breakpointHit();

        synchronized(semaphore) {
            //breakPanel.breakpointHit();
            if (breakMgmt.isHoldMessage(aMessage)) {
                setBreakDisplay(aMessage, false);
                waitUntilContinue(aMessage, false);
            }
        }
        clearAndDisableResponse();

        return ! breakMgmt.isToBeDropped();
    }
    
    private void setBreakDisplay(final Message msg, boolean isRequest) {
        setHttpDisplay(breakMgmt, msg, isRequest);
        breakMgmt.breakpointDisplayed();
        if (View.isInitialised()) {
	        try {
	            EventQueue.invokeAndWait(new Runnable() {
	                @Override
	                public void run() {
	                    View.getSingleton().getMainFrame().toFront();
	                }
	            });
	        } catch (Exception e) {
	            logger.warn(e.getMessage(), e);
	        }
        }
    }
    
    private void setHttpDisplay(final BreakpointManagementInterface breakPanel, final Message msg, final boolean isRequest) {
        try {
            EventQueue.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    breakPanel.setMessage(msg, isRequest);
                }
            });
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
        
    }
    
    private void waitUntilContinue(Message aMessage, final boolean isRequest) {
        // Note that multiple requests and responses can get built up, so pressing continue only
        // releases the current break, not all of them.
        //breakPanel.setContinue(false);
        while (breakMgmt.isHoldMessage(aMessage)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logger.warn(e.getMessage(), e);
            }
        }
        try {
            EventQueue.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    breakMgmt.saveMessage(isRequest);
                }
            });
        } catch (Exception ie) {
            logger.warn(ie.getMessage(), ie);
        }
    }

    /**
     * You have to handle {@link Mode#safe} outside.
     * 
     * @param aMessage
     * @param isRequest
     * @param onlyIfInScope
     * @return True if a breakpoint for given message exists.
     */
    public boolean isBreakpoint(Message aMessage, boolean isRequest, boolean onlyIfInScope) {
    	if (aMessage.isForceIntercept()) {
			// The browser told us to do it Your Honour
			return true;
    	}
    	
    	/* Disable pending other changes
    	String secHeader = aMessage.getHeader(HttpHeader.X_SECURITY_PROXY);
    	if (secHeader != null) {
			for (String val : secHeader.split(",")) {
				if (val.trim().startsWith(HttpHeader.SEC_PROXY_KEY) && val.indexOf("=") > 0) {
					String[] keyValue = val.split("=");
					// Have we been told to intercept messages with this key?
					for (String k : this.enabledKeyBreakpoints) {
						if (k.equals(keyValue[1].trim())) {
							// Yes, we have
							logger.debug("isBreakpoint match on key " + k);
							return true;
						}
					}
				}
    		}
    	}
    	*/
    	
    	if (onlyIfInScope && ! aMessage.isInScope()) {
    		return false;
    	}
    	
        if (isBreakOnAllRequests(aMessage, isRequest)) {
            // Break on all requests
            return true;
        } else if (isBreakOnAllResponses(aMessage, isRequest)) {
            // Break on all responses
            return true;
        } else if (isBreakOnStepping(aMessage, isRequest)) {
            // Stopping through all requests and responses
            return true;
        }
        
        return isBreakOnEnabledBreakpoint(aMessage, isRequest, onlyIfInScope);
    }

	protected boolean isBreakOnAllRequests(Message aMessage, boolean isRequest) {
    	return isRequest && breakMgmt.isBreakRequest();
	}
    
	protected boolean isBreakOnAllResponses(Message aMessage, boolean isRequest) {
    	return !isRequest && breakMgmt.isBreakResponse();
	}

	protected boolean isBreakOnStepping(Message aMessage, boolean isRequest) {
		return breakMgmt.isStepping();
	}

    protected boolean isBreakOnEnabledBreakpoint(Message aMessage, boolean isRequest, boolean onlyIfInScope) {
		if (enabledBreakpoints.isEmpty()) {
            // No break points
            return false;
        }
        
        // match against the break points
        synchronized (enabledBreakpoints) {
            Iterator<BreakpointMessageInterface> it = enabledBreakpoints.iterator();
            
            while(it.hasNext()) {
                BreakpointMessageInterface breakpoint = it.next();
                
                if (breakpoint.match(aMessage, isRequest, onlyIfInScope)) {
                    return true;
                }
            }
        }

        return false;
	}
    
	private void clearAndDisableRequest() {
        if (EventQueue.isDispatchThread()) {
            breakMgmt.clearAndDisableRequest();
        } else {
            try {
                EventQueue.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        breakMgmt.clearAndDisableRequest();
                    }
                });
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        }
    }
    
    private void clearAndDisableResponse() {
        if (EventQueue.isDispatchThread()) {
            breakMgmt.clearAndDisableResponse();
        } else {
            try {
                EventQueue.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        breakMgmt.clearAndDisableResponse();
                    }
                });
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        }
    }
}
