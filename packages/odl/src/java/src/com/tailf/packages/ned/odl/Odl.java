package com.tailf.packages.ned.odl;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.http.Consts;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;

import com.tailf.conf.ConfXMLParam;
import com.tailf.maapi.Maapi;

public class Odl {
    private CloseableHttpClient client;
    private CookieStore cookieStore = new BasicCookieStore();
    private String baseUrl = null;
    
    private String user;
    private String pass;
    
    private static Logger LOGGER = Logger.getLogger(Odl.class);
    
    public Odl(InetAddress ip, int port, String user, String pass) {
        this.user = user;
        this.pass = pass;
        
        client = HttpClients.custom()
                .setDefaultCookieStore(cookieStore).build();
        baseUrl = String.format("http://%s:%d", ip.getHostAddress(), port);
        LOGGER.debug("base url: "+baseUrl);
    }
    
    private String httpPost(String uri, String input)
    {
        InputStream in ;
        StringEntity entity = new StringEntity(input, ContentType.create(
                "application/xml", Consts.UTF_8));
        entity.setChunked(true);
        HttpPost httppost = new HttpPost(this.baseUrl + uri);
        httppost.setEntity(entity);
        
        String encoding = new String(Base64.encodeBase64(String.format("%s:%s", this.user, this.pass).getBytes()));
        httppost.setHeader("Authorization", "Basic " + encoding);
        httppost.setHeader("Accept", "application/xml");
        
        LOGGER.debug("http POST: "+uri);
        try {
            HttpResponse response = client.execute(httppost);
            LOGGER.debug("response:\n"+response.toString());
            if(response.getStatusLine().getStatusCode()!=200)
            {
                throw new RuntimeException(response.getStatusLine().toString());
            }
            in=response.getEntity().getContent();
            String body = IOUtils.toString(in);
            LOGGER.trace("body:\n"+body);
            return body;
        } catch (ClientProtocolException e) {
            LOGGER.error(e);
            throw new RuntimeException("http POST failed. ", e);
        } catch (IOException e) {
            LOGGER.error(e);
            throw new RuntimeException("http POST failed. ", e);
        }
    }
    
    private String httpGet(String uri)
    {
        InputStream in ;
        HttpGet httpget = new HttpGet(this.baseUrl + uri);
        
        String encoding = new String(Base64.encodeBase64(String.format("%s:%s", this.user, this.pass).getBytes()));
        httpget.setHeader("Authorization", "Basic " + encoding);
        httpget.setHeader("Accept", "application/xml");

        LOGGER.debug("http GET: "+uri);
        try {
            HttpResponse response = client.execute(httpget);
            LOGGER.debug("response:\n"+response.toString());
            if(response.getStatusLine().getStatusCode()!=200)
            {
                throw new RuntimeException(response.getStatusLine().toString());
            }
            in=response.getEntity().getContent();
            String body = IOUtils.toString(in);
            LOGGER.trace("body:\n"+body);
            return body;
        } catch (ClientProtocolException e) {
            LOGGER.error(e);
            throw new RuntimeException("http GET failed. ", e);
        } catch (IOException e) {
            LOGGER.error(e);
            throw new RuntimeException("http GET failed. ", e);
        }
    }
    
    private static Document getXMLFromString(String xml) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(xml));
        return builder.parse(is);
    }
    
    private static String getStringFromXML(Document doc){
        DOMImplementationLS domImplementation = (DOMImplementationLS) doc.getImplementation();
        LSSerializer lsSerializer = domImplementation.createLSSerializer();
        return lsSerializer.writeToString(doc).replaceAll("\\<\\?xml(.+?)\\?\\>", "").trim();
    }
    
    public void getTopology(int tHandle, Maapi maapi, String confPath) throws Exception {
        String uri = "/restconf/operational/network-topology:network-topology/";
        String response = httpGet(uri);
        Document document = getXMLFromString(response);
        NodeList topologyList = document.getFirstChild().getChildNodes();
        for(int i = 0; i < topologyList.getLength(); i++) {
            String topologyType = "";
            String topologyId = "";
            Node topology = topologyList.item(i);
            NodeList topologyChildList = topology.getChildNodes();
            ArrayList<Node> nodeList = new ArrayList<Node>();
            for(int j = 0; j < topologyChildList.getLength(); j++) {
                Node topologyChild = topologyChildList.item(j);
                String nodeName = topologyChild.getNodeName();
                if(nodeName == "topology-types") {
                    if(topologyChild.hasChildNodes()) {
                        topologyType = topologyChild.getFirstChild().getNodeName();
                    }
                }else if(nodeName == "topology-id") {
                    topologyId = topologyChild.getTextContent();
                }else if(nodeName == "node") {
                    nodeList.add(topologyChild);
                }
            }
            
            if(topologyType == "topology-pcep") {
                String topoPath = String.format(confPath + "/odl:pcep-topology{%s}", topologyId);
                maapi.create(tHandle, topoPath);
                for(Node node : nodeList) {
                    String nodeId = "";
                    Node pcc = null;
                    NodeList nodeChildList = node.getChildNodes();
                    for(int j = 0; j < nodeChildList.getLength(); j++) {
                        Node nodeChild = nodeChildList.item(j);
                        String nodeName = nodeChild.getNodeName();
                        if(nodeName == "node-id") {
                            nodeId = nodeChild.getTextContent();
                        }else if(nodeName == "path-computation-client") {
                            pcc = nodeChild;
                        }
                    }
                    if(nodeId != "") {
                        String nodePath = String.format(topoPath + "/node{%s}", nodeId);
                        maapi.create(tHandle, nodePath);
                        if(pcc != null) {
                            String pccPath = nodePath + "/path-computation-client";
                            NodeList pccChildList = pcc.getChildNodes();
                            for(int k = 0; k < pccChildList.getLength(); k++) {
                                Node pccChild = pccChildList.item(k);
                                String nodeName = pccChild.getNodeName();
                                if(nodeName == "ip-address") {
                                    maapi.setElem(tHandle, pccChild.getTextContent(), pccPath + "/ip-address");
                                }else if(nodeName == "state-sync") {
                                    maapi.setElem(tHandle, pccChild.getTextContent(), pccPath + "/state-sync");
                                }
                            }
                        }
                    }
                }
            }else {
                
            }
        }
    }
    
    public void addLsp(ConfXMLParam[] p) throws Exception {
        String uri = "/restconf/operations/network-topology-pcep:add-lsp";
        LOGGER.debug("add lsp");
        
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("input");
        rootElement.setAttribute("xmlns", "urn:opendaylight:params:xml:ns:yang:topology:pcep");
        doc.appendChild(rootElement);
        
        Document input = ConfXMLParam.toDOM(p);
        String XMLele = ConfXMLParam.toXML(p);
        LOGGER.debug(XMLele);
        
        //node
        String nodeValue = input.getElementsByTagName("odl:node").item(0).getTextContent();
        Element nodeElement = doc.createElement("node");
        nodeElement.setTextContent(nodeValue);
        rootElement.appendChild(nodeElement);
        
        //name
        String nameValue = input.getElementsByTagName("odl:name").item(0).getTextContent();
        Element nameElement = doc.createElement("name");
        nameElement.setTextContent(nameValue);
        rootElement.appendChild(nameElement);
        
        //arguments
        Element argumentsElement = doc.createElement("arguments");
        rootElement.appendChild(argumentsElement);
        
        //lsp attributes
        Element lspElement = doc.createElement("lsp");
        lspElement.setAttribute("xmlns", "urn:opendaylight:params:xml:ns:yang:pcep:ietf:stateful");
        argumentsElement.appendChild(lspElement);
        
        NodeList lspNodeList = input.getElementsByTagName("odl:lsp").item(0).getChildNodes();
        for(int i = 0; i < lspNodeList.getLength(); i++) {
            Node lspNode = lspNodeList.item(i);
            String eleName = lspNode.getNodeName().split(":")[1];
            String eleValue = lspNode.getTextContent();
            Element element = doc.createElement(eleName);
            element.setTextContent(eleValue);
            lspElement.appendChild(element);
        }
        
        //endpoints object
        Element endpointsElement = doc.createElement("endpoints-obj");
        argumentsElement.appendChild(endpointsElement);
        
        NodeList epoNodeList = input.getElementsByTagName("odl:endpoints-obj").item(0).getChildNodes();
        for(int i = 0; i < epoNodeList.getLength(); i++) {
            Node epoNode = epoNodeList.item(i);
            String epoName = epoNode.getNodeName().split(":")[1];
            Element epoElement = doc.createElement(epoName);
            endpointsElement.appendChild(epoElement);
            NodeList epoSubNodeList = epoNode.getChildNodes();
            for(int j = 0; j < epoSubNodeList.getLength(); j++) {
                Node epoSubNode = epoSubNodeList.item(j);
                String epoSubName = epoSubNode.getNodeName().split(":")[1];
                String epoSubValue = epoSubNode.getTextContent();
                Element epoSubElement = doc.createElement(epoSubName);
                epoSubElement.setTextContent(epoSubValue);
                epoElement.appendChild(epoSubElement);
            }
        }
        
        //explicit route object
        Element explicitElement = doc.createElement("ero");
        argumentsElement.appendChild(explicitElement);
        
        NodeList eroNodeList = input.getElementsByTagName("odl:ero").item(0).getChildNodes();
        for(int i = 0; i < eroNodeList.getLength(); i++) {
            Node eroNode = eroNodeList.item(i);
            String eroName = eroNode.getNodeName().split(":")[1];
            Element eroElement = doc.createElement(eroName);
            explicitElement.appendChild(eroElement);
            NodeList eroSubNodeList = eroNode.getChildNodes();
            for(int j = 0; j < eroSubNodeList.getLength(); j++) {
                Node eroSubNode = eroSubNodeList.item(j);
                String eroSubName = eroSubNode.getNodeName().split(":")[1];
                String eroSubValue = eroSubNode.getTextContent();
                Element eroSubElement = doc.createElement(eroSubName);
                if(eroSubName.equals("ip-prefix")) {
                    Element prefixElement = doc.createElement("ip-prefix");
                    prefixElement.setTextContent(eroSubValue);
                    eroSubElement.appendChild(prefixElement);
                }else {
                    eroSubElement.setTextContent(eroSubValue);
                }
                eroElement.appendChild(eroSubElement);
            }
        }
        
        //network topology
        String topologyValue = input.getElementsByTagName("odl:network-topology").item(0).getTextContent();
        Element topologyElement = doc.createElement("network-topology-ref");
        topologyElement.setAttribute("xmlns:topo", "urn:TBD:params:xml:ns:yang:network-topology");
        topologyElement.setTextContent(String.format("/topo:network-topology/topo:topology[topo:topology-id=\"%s\"]", topologyValue));
        rootElement.appendChild(topologyElement);
        
        LOGGER.debug(getStringFromXML(doc));
        httpPost(uri, getStringFromXML(doc));
    }
    
    public void updateLsp(ConfXMLParam[] p) throws Exception {
        String uri = "/restconf/operations/network-topology-pcep:update-lsp";
        LOGGER.debug("update lsp");
        
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("input");
        rootElement.setAttribute("xmlns", "urn:opendaylight:params:xml:ns:yang:topology:pcep");
        doc.appendChild(rootElement);
        Document input = ConfXMLParam.toDOM(p);
        
        //node
        String nodeValue = input.getElementsByTagName("odl:node").item(0).getTextContent();
        Element nodeElement = doc.createElement("node");
        nodeElement.setTextContent(nodeValue);
        rootElement.appendChild(nodeElement);
        
        //name
        String nameValue = input.getElementsByTagName("odl:name").item(0).getTextContent();
        Element nameElement = doc.createElement("name");
        nameElement.setTextContent(nameValue);
        rootElement.appendChild(nameElement);
        
        //arguments
        Element argumentsElement = doc.createElement("arguments");
        rootElement.appendChild(argumentsElement);
        
        //lsp attributes
        Element lspElement = doc.createElement("lsp");
        lspElement.setAttribute("xmlns", "urn:opendaylight:params:xml:ns:yang:pcep:ietf:stateful");
        argumentsElement.appendChild(lspElement);
        
        NodeList lspNodeList = input.getElementsByTagName("odl:lsp").item(0).getChildNodes();
        for(int i = 0; i < lspNodeList.getLength(); i++) {
            Node lspNode = lspNodeList.item(i);
            String eleName = lspNode.getNodeName().split(":")[1];
            String eleValue = lspNode.getTextContent();
            Element element = doc.createElement(eleName);
            element.setTextContent(eleValue);
            lspElement.appendChild(element);
        }
        
        //explicit route object
        Element explicitElement = doc.createElement("ero");
        argumentsElement.appendChild(explicitElement);
        
        NodeList eroNodeList = input.getElementsByTagName("odl:ero").item(0).getChildNodes();
        for(int i = 0; i < eroNodeList.getLength(); i++) {
            Node eroNode = eroNodeList.item(i);
            String eroName = eroNode.getNodeName().split(":")[1];
            Element eroElement = doc.createElement(eroName);
            explicitElement.appendChild(eroElement);
            NodeList eroSubNodeList = eroNode.getChildNodes();
            for(int j = 0; j < eroSubNodeList.getLength(); j++) {
                Node eroSubNode = eroSubNodeList.item(j);
                String eroSubName = eroSubNode.getNodeName().split(":")[1];
                String eroSubValue = eroSubNode.getTextContent();
                Element eroSubElement = doc.createElement(eroSubName);
                if(eroSubName.equals("ip-prefix")) {
                    Element prefixElement = doc.createElement("ip-prefix");
                    prefixElement.setTextContent(eroSubValue);
                    eroSubElement.appendChild(prefixElement);
                }else {
                    eroSubElement.setTextContent(eroSubValue);
                }
                eroElement.appendChild(eroSubElement);
            }
        }
        
        //network topology
        String topologyValue = input.getElementsByTagName("odl:network-topology").item(0).getTextContent();
        Element topologyElement = doc.createElement("network-topology-ref");
        topologyElement.setAttribute("xmlns:topo", "urn:TBD:params:xml:ns:yang:network-topology");
        topologyElement.setTextContent(String.format("/topo:network-topology/topo:topology[topo:topology-id=\"%s\"]", topologyValue));
        rootElement.appendChild(topologyElement);
        
        LOGGER.debug(getStringFromXML(doc));
        httpPost(uri, getStringFromXML(doc));
    }
    
    public void removeLsp(ConfXMLParam[] p) throws Exception {
        String uri = "/restconf/operations/network-topology-pcep:remove-lsp";
        LOGGER.debug("remove lsp");
        
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("input");
        rootElement.setAttribute("xmlns", "urn:opendaylight:params:xml:ns:yang:topology:pcep");
        doc.appendChild(rootElement);
        
        Document input = ConfXMLParam.toDOM(p);
        
        //node
        String nodeValue = input.getElementsByTagName("odl:node").item(0).getTextContent();
        Element nodeElement = doc.createElement("node");
        nodeElement.setTextContent(nodeValue);
        rootElement.appendChild(nodeElement);
        
        //name
        String nameValue = input.getElementsByTagName("odl:name").item(0).getTextContent();
        Element nameElement = doc.createElement("name");
        nameElement.setTextContent(nameValue);
        rootElement.appendChild(nameElement);
        
        //network topology
        String topologyValue = input.getElementsByTagName("odl:network-topology").item(0).getTextContent();
        Element topologyElement = doc.createElement("network-topology-ref");
        topologyElement.setAttribute("xmlns:topo", "urn:TBD:params:xml:ns:yang:network-topology");
        topologyElement.setTextContent(String.format("/topo:network-topology/topo:topology[topo:topology-id=\"%s\"]", topologyValue));
        rootElement.appendChild(topologyElement);
        
        LOGGER.debug(getStringFromXML(doc));
        httpPost(uri, getStringFromXML(doc));
    }

   public void addLspSR(ConfXMLParam[] p) throws Exception {
        String uri = "/restconf/operations/network-topology-pcep:add-lsp";
        LOGGER.debug("add lsp sr");
        
        String XMLele = ConfXMLParam.toXML(p,"input","");
        String res = modify_sr(XMLele);
        LOGGER.debug(res);
        //LOGGER.debug(getStringFromXML(doc));
        httpPost(uri, res);
    }

   public void updateLspSR(ConfXMLParam[] p) throws Exception {
        String uri = "/restconf/operations/network-topology-pcep:update-lsp";
        LOGGER.debug("update lsp sr");
        
        String XMLele = ConfXMLParam.toXML(p,"input","");
        String res = modify_sr(XMLele);
        LOGGER.debug(res);
        
        //LOGGER.debug(getStringFromXML(doc));
        httpPost(uri, res);
    }

   public void removeLspSR(ConfXMLParam[] p) throws Exception {
        String uri = "/restconf/operations/network-topology-pcep:remove-lsp";
        LOGGER.debug("remove lsp sr");
        
        String XMLele = ConfXMLParam.toXML(p,"input","");
        String res = modify_sr(XMLele);
        LOGGER.debug(res);
        //LOGGER.debug(getStringFromXML(doc));
        httpPost(uri, res);
    }

	static String add_after(String o,String a,String b){
		return o.replace("<" + a + ">","<" + a+b + ">");
	}
	public static String modify_sr(String content)throws Exception{
		final String nsrouting=" xmlns=\"urn:opendaylight:params:xml:ns:yang:pcep:segment:routing\"";
		final String nspcep = " xmlns=\"urn:opendaylight:params:xml:ns:yang:topology:pcep\"";
		final String nsstateful = " xmlns=\"urn:opendaylight:params:xml:ns:yang:pcep:ietf:stateful\"";
		String c1 = content.replace("odl:","");
        String c2 = c1.replace(":odl", "");
        String c3 = c2.replace(" xmlns=\"http://www.tailf.com\"", "");
        String c4 = c3.replace(" xmlns=\"http://tail-f.com/ned/odl\"","");
        String c5 = add_after(c4,"input",nspcep);
        String c6 = add_after(c5,"lsp",nsstateful);
        String c7 = add_after(c6,"path-setup-type",nsstateful);
        String c8 = add_after(c7,"sid-type",nsrouting);
        String c9 = add_after(c8,"m-flag",nsrouting);
        String c10 = add_after(c9,"sid",nsrouting);
        String c11 = add_after(c10,"ip-address",nsrouting);
        String c12 = add_after(c11,"local-ip-address",nsrouting);
        String c13 = add_after(c12,"remote-ip-address",nsrouting);
        String c14 = c13.replace("<network-topology>","<network-topology-ref xmlns:topo=\"urn:TBD:params:xml:ns:yang:network-topology\">/topo:network-topology/topo:topology[topo:topology-id=\"");
        String c15 = c14.replace("</network-topology>","\"]</network-topology-ref>");
        return c15;
	}
}
