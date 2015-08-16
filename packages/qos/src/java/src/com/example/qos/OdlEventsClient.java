package com.example.qos;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import com.tailf.conf.Conf;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfUInt16;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiUserSessionFlag;


public class OdlEventsClient {
	
	 private static OdlEventsClient instance;
	 
		private OdlEventsClient(){
			String destUri = "ws://10.79.44.247:8185/network-topology:network-topology/network-topology:topology/example-linkstate-topology/datastore=OPERATIONAL/scope=SUBTREE";
		       
			WebSocketClient client = new WebSocketClient();
	        SimpleEchoSocket socket = new SimpleEchoSocket();
	        try {
	            client.start();
	            URI echoUri = new URI(destUri);
	            ClientUpgradeRequest request = new ClientUpgradeRequest();
	            client.connect(socket, echoUri, request);
	            System.out.printf("Connecting to : %s%n", echoUri);
	            socket.awaitClose(30, TimeUnit.MINUTES);
	            
	            
	        } catch (Throwable t) {
	            t.printStackTrace();
	        } finally {
	            try {
	            	client.stop();
	            } catch (Exception e) {
	                e.printStackTrace();
	            }
	        }
		}
	 
	      public static synchronized OdlEventsClient getInstance() {  
		      if (instance == null) {  
		          instance = new OdlEventsClient();  
		      }  
		      return instance;  
		      }  
		 
	    public static void main(String[] args) {
	        String destUri = "ws://10.79.44.247:8185/network-topology:network-topology/network-topology:topology/example-linkstate-topology/datastore=OPERATIONAL/scope=SUBTREE";
	       
	        try {
				changePath();
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ConfException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        
	        WebSocketClient client = new WebSocketClient();
	        SimpleEchoSocket socket = new SimpleEchoSocket();
	        try {
	            client.start();
	            URI echoUri = new URI(destUri);
	            ClientUpgradeRequest request = new ClientUpgradeRequest();
	            client.connect(socket, echoUri, request);
	            System.out.printf("Connecting to : %s%n", echoUri);
	            socket.awaitClose(30, TimeUnit.SECONDS);
	            
	            
	        } catch (Throwable t) {
	            t.printStackTrace();
	        } finally {
	            try {
	                client.stop();
	            } catch (Exception e) {
	                e.printStackTrace();
	            }
	        }
	    }
	    
	    public static void changePath() throws UnknownHostException, IOException, ConfException{
	    	Socket socket = new Socket("localhost",Conf.NCS_PORT);
	        Maapi maapi = new Maapi(socket);
	        
	        maapi.startUserSession("admin",
                    InetAddress.getLocalHost(),
                    "maapi",
                    new String[] {"admin"},
                    MaapiUserSessionFlag.PROTO_TCP);
	        
	        int th = maapi.startTrans(Conf.DB_RUNNING,
                    Conf.MODE_READ_WRITE);
	        
	        //maapi.getElem(th, "/ncs:services/qos{c_qos}/dst-endpoint");
//	        maapi.setElem(th,
//	                  new ConfBuf("100.0.0.2"),
//	                  "/ncs:services/qos{c_qos}/dst-endpoint");
	        
	        maapi.setElem(th,
	                  new ConfBool(true),
	                  "/ncs:services/qos{c_qos}/isNotified");
	        
	        maapi.applyTrans(th, false);
	    }
	    @WebSocket(maxTextMessageSize = 64 * 1024)
	    public static class SimpleEchoSocket {
	     
	        private final CountDownLatch closeLatch;
	        
	        @SuppressWarnings("unused")
	        private Session session;
	     
	        public SimpleEchoSocket() {
	            this.closeLatch = new CountDownLatch(1);
	        }
	     
	        public boolean awaitClose(int duration, TimeUnit unit) throws InterruptedException {
	            return this.closeLatch.await(duration, unit);
	        }
	     
	        @OnWebSocketClose
	        public void onClose(int statusCode, String reason) {
	            System.out.printf("Connection closed: %d - %s%n", statusCode, reason);
	            this.session = null;
	            this.closeLatch.countDown();
	        }
	     
	        @OnWebSocketConnect
	        public void onConnect(Session session) {
	            System.out.printf("Got connect: %s%n", session);
	            this.session = session;
	            try {
	                Future<Void> fut;
	                fut = session.getRemote().sendStringByFuture("Hello");
	                fut.get(2, TimeUnit.SECONDS);
	                fut = session.getRemote().sendStringByFuture("Thanks for the conversation.");
	                fut.get(2, TimeUnit.SECONDS);
	               // session.close(StatusCode.NORMAL, "I'm done");
	            } catch (Throwable t) {
	                t.printStackTrace();
	            }
	        }
	     
	        @OnWebSocketMessage
	        public void onMessage(String msg) {
	            System.out.printf("Got msg: %s%n", msg);
	            
	            try {
					changePath();
				} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ConfException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        }
	    }
}
