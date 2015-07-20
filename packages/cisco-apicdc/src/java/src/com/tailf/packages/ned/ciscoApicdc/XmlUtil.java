package com.tailf.packages.ned.ciscoApicdc;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.log4j.Logger;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;



public class XmlUtil {

        private static Document document;

        private static Logger LOGGER = Logger.getLogger(XmlUtil.class);
        private static String styleSheet =
  "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"+
  "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "+
  "version=\"1.0\">"+
  "<xsl:output indent=\"yes\" />"+
  "<xsl:strip-space elements=\"*\" />"+
  "<xsl:template match=\"*\">"+
    "<xsl:copy>"+
      "<xsl:if test=\"@*\">"+
        "<xsl:for-each select=\"@*\">"+
          "<xsl:element name=\"{name()}\">"+
            "<xsl:value-of select=\".\" />"+
          "</xsl:element>"+
        "</xsl:for-each>"+
      "</xsl:if>"+
      "<xsl:apply-templates />"+
    "</xsl:copy>"+
  "</xsl:template>"+
  "</xsl:stylesheet>";

        private class NodeInfo
        {
            public String id;
            public Node node;

            public NodeInfo(Node node, String id)
            {
                this.node=node;
                this.id=id;
            }
        }


        private String baseUrl;
        private String device;
        private CloseableHttpClient client;

        public XmlUtil(String baseUrl, String device,
                CloseableHttpClient client )
        {
            this.baseUrl = baseUrl;
            this.device = device;
            this.client = client;
        }
        /**
         *
         * @param inputStream
         * @return
         * @throws ParserConfigurationException
         * @throws SAXException
         * @throws IOException
         * @throws TransformerException
         */
        public String getDeviceConfig(ByteArrayInputStream inputStream)
                throws ParserConfigurationException, SAXException,
                        IOException, TransformerException

        {
            /*
             * First transform device xml format to ncs xml format,
             * using the xsl style sheet.
             *
             * Devicedata:
             * <fvTenant childAction="" descr="" dn="uni/tn-common"
             * lcOwn="local" modTs="2015-01-04T19:06:37.664+00:00"
             * monPolDn="uni/tn-common/monepg-default" name="common"
             * ownerKey="" ownerTag="" status="" uid="0"/>
             *
             *
             * Ncs style:

                <fvTenant>
                  <childAction>""</childAction>
                  <descr>""</descr>
                  <dn>"uni/tn-common"</dn>
                  <lcOwn>"local"</lcOwn>
                  <modTs>"2015-01-04T19:06:37.664+00:00"</modTs>
                  <monPolDn>"uni/tn-common/monepg-default"</monPolDn>
                  <name>"common"</name>
                  <ownerKey>""/<ownerKey>
                  <ownerTag>""</ownerTag>
                  <status>""/<status>
                  <uid>"0"</uid>
                </fvTenant>

             */


            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(inputStream);

            ByteArrayInputStream styleStream =
                    new ByteArrayInputStream(styleSheet.getBytes("UTF-8"));
            StreamSource styleSource = new StreamSource(styleStream);
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer(styleSource);
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            DOMSource source = new DOMSource(document);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            StreamResult result = new StreamResult(outputStream);
            transformer.transform(source, result);

            /*
             *  Create a source DOM with the device config
             */
            Document sourceDoc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(
                            outputStream.toByteArray()));

            /*
             *  Create destination DOM that will hold the NCS
             *  readable XML dump
             */
            Document destDoc =  factory.newDocumentBuilder().newDocument();

            /*
             * Build the XML root elements.
             *
             * Will make a header like this
             * <?xml version="1.0" encoding="UTF-8"?>
             *  <config xmlns="http://tail-f.com/ns/config/1.0">
             *      <devices xmlns="http://tail-f.com/ns/ncs">
             *          <device>
             *              <name>apic</name>
             */
            Element newRoot = destDoc.
                    createElementNS("http://tail-f.com/ns/config/1.0",
                            "config");
            destDoc.appendChild(newRoot);
            Node n = newRoot.
                    appendChild(destDoc.
                            createElementNS("http://tail-f.com/ns/ncs",
                                    "devices"));
            n = n.appendChild(destDoc.createElement("device"));
            Node name = n.appendChild(destDoc.createElement("name"));
            name.setTextContent(device);
            n = n.appendChild(destDoc.createElement("config"));

            /*
             * Import the device config
             */
            n = n.appendChild(newRoot.
                    appendChild(destDoc.
                            importNode(sourceDoc.
                                    getDocumentElement(),
                                    true)));

            /*
             * Add element "config".
             */
            destDoc.renameNode(n, "", "apic");

            /*
             * Remove all config nodes named "totalCount",
             */
            modifyConfig(destDoc.getDocumentElement(),
                    Op.DELETE_WITH_NAME,
                    "totalCount", null, null);

            /*
             * Add proper namespace tags to all top list entries
             */
            modifyConfig(destDoc.getDocumentElement(),
                    Op.SET_NAMESPACE,
                    "apic", "http://tail-f.com/ned/cisco-apic", null);

            return getSubTrees(transformer, destDoc);
        }

        private String getSubTrees(Transformer transformer,
                Document destDoc)
                throws TransformerException {
            /*
             * Collect the subtrees.
             */
            HashMap<Integer, ArrayList< NodeInfo>> list =
                    new HashMap<Integer, ArrayList< NodeInfo>>();
            modifyConfig(destDoc.getDocumentElement(), Op.COLLECT_OBJECT_IDS,
                    "","", list);

            LOGGER.debug("Collected subtrees:");
            LOGGER.debug(list.toString());

            /*
             * Sort the nodes by id, since they may be returned in any
             * order from the device. The checksum calculated for transaction
             * ID requires the same order of the nodes.
             */
            sortNodes(list);

            /*
             * read the content of the top objects from device
             */
            for (NodeInfo subTree : list.get(1)) {
                LOGGER.debug("sub trees: " + subTree.id);
//                if(tenant.id.equals("uni/tn-Test-NED-1"))  //TODO: remove
                {
                    Document subTreeDoc = getSubTree(subTree.id);
                    if (subTreeDoc == null) {
                        throw new RuntimeException("Failed to retrive subtree");
                    }
                    /*
                     * put subTreeDoc in right node....
                     */
                    HashMap<Integer, ArrayList<NodeInfo>> SubtreeList =
                            new HashMap<Integer, ArrayList<NodeInfo>>();
                    modifyConfig(subTreeDoc.getDocumentElement(),
                            Op.COLLECT_OBJECT_IDS, "", "", SubtreeList);
                    //Add this to right place in destDoc
                    NodeInfo parentNodeInfo = subTree;
                    LOGGER.debug("SubtreeList.size(): " + SubtreeList.size());
                    if (SubtreeList.size() > 0) {
                        ArrayList<NodeInfo> currentNodeInfos =
                                SubtreeList.get(2);
                        //First add level below tenants
                        addSubtree(destDoc, parentNodeInfo, 2,
                                currentNodeInfos);
                        //Then add sub levels
                        for (int position = 2;
                             position < 2 + SubtreeList.keySet().size() - 2;
                             position++) {
                            currentNodeInfos = SubtreeList.get(position);
                            for (NodeInfo currentNode : currentNodeInfos) {
                                addSubtree(destDoc, currentNode, position + 1,
                                        SubtreeList.get(position + 1));
                            }
                        }
                    }
                }
            }

            //value "" is regarded as attribute not set.
            modifyConfig(destDoc.getDocumentElement(),
                    Op.DELETE_WITH_VALUE, "", "", null);
            modifyConfig(destDoc.getDocumentElement(),
                    Op.SORT_NODE_FIRST,
                    "tDn", // xpath
                    "", null);  // key name
            modifyConfig(destDoc.getDocumentElement(),
                    Op.SORT_NODE_FIRST,
                    "ip", // xpath
                    "", null);  // key name
            modifyConfig(destDoc.getDocumentElement(),
                    Op.SORT_NODE_FIRST,
                    "type", // xpath
                    "", null);  // key name
            modifyConfig(destDoc.getDocumentElement(),
                    Op.SORT_NODE_FIRST,
                    "name", // xpath
                    "", null);  // key name
            modifyConfig(destDoc.getDocumentElement(),
                    Op.SORT_NODE_FIRST,
                    "tnFvBDName", // xpath
                    "", null);  // key name
            modifyConfig(destDoc.getDocumentElement(),
                    Op.SORT_NODE_FIRST,
                    "tnVzBrCPName", // xpath
                    "", null);  // key name
            modifyConfig(destDoc.getDocumentElement(),
                    Op.SORT_NODE_FIRST,
                    "tnFvCtxName", // xpath
                    "", null);  // key name
            modifyConfig(destDoc.getDocumentElement(),
                    Op.SORT_NODE_FIRST,
                    "tnVzFilterName", // xpath
                    "", null);  // key name
            modifyConfig(destDoc.getDocumentElement(),
                    Op.SORT_NODE_FIRST,
                    "vendor", // xpath
                    "", null);  // key name
            modifyConfig(destDoc.getDocumentElement(),
                    Op.SORT_NODE_FIRST,
                    "tnFabricHIfPolName", // xpath
                    "", null);  // key name
            modifyConfig(destDoc.getDocumentElement(),
                    Op.SORT_NODE_FIRST,
                    "tnCdpIfPolName", // xpath
                    "", null);  // key name
            modifyConfig(destDoc.getDocumentElement(),
                    Op.SORT_NODE_FIRST,
                    "tnLacpLagPolName", // xpath
                    "", null);  // key name


            /*
             * transform xml to String
             */
            StringWriter out = new StringWriter();
            transformer.transform(new DOMSource(destDoc.
                    getDocumentElement()),
                    new StreamResult(out));
            String xml = out.getBuffer().toString();
            if(LOGGER.isTraceEnabled())
            {
                LOGGER.trace("===================");
                LOGGER.trace(xml);
            }

            return xml;
        }
        private void addSubtree(Document destDoc, NodeInfo parentNodeInfo,
                int position, ArrayList<NodeInfo> currentNodeInfos)
                throws DOMException {
            for( NodeInfo currentNodeInfo: currentNodeInfos)
            {
                if(LOGGER.isTraceEnabled())
                {
                    LOGGER.trace("currentNodeInfo: "+currentNodeInfo.id);
                    LOGGER.trace("parentId: "+parentNodeInfo.id);
                    LOGGER.trace("position: "+position);
                }
                if(!currentNodeInfo.id.startsWith(parentNodeInfo.id))
                {
                    LOGGER.trace("continue!");
                    continue;
                }
                Node currentNode = currentNodeInfo.node;
                if(!currentNode.getNodeName()
                        .equals("fvTenant") )
                {
                    Node newNode =
                            destDoc.adoptNode(currentNode);
                    parentNodeInfo.node.appendChild(newNode);
                }
            }
        }

        private Document getSubTreeConfig(ByteArrayInputStream input)
                throws ParserConfigurationException, SAXException,
                        IOException, TransformerException

        {
            /*
             */
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(input);

            ByteArrayInputStream styleStream =
                    new ByteArrayInputStream(styleSheet.getBytes("UTF-8"));
            StreamSource styleSource = new StreamSource(styleStream);
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer(styleSource);

            DOMSource source = new DOMSource(document);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            StreamResult result = new StreamResult(outputStream);
            transformer.transform(source, result);


            /*
             *  Create a source DOM with the device config
             */
            Document sourceDoc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(
                            outputStream.toByteArray()));

            StringWriter in = new StringWriter();
            transformer.transform(new DOMSource(sourceDoc.
                    getDocumentElement()),
                    new StreamResult(in));
            if(LOGGER.isTraceEnabled())
            {
                LOGGER.trace("Sub tree raw output:");
                LOGGER.trace(in.getBuffer().toString());

                LOGGER.trace(sourceDoc.getDocumentElement().getNodeName());
                LOGGER.trace(sourceDoc.getDocumentElement().getNodeValue());
            }
            StringWriter out = new StringWriter();
            transformer.transform(new DOMSource(sourceDoc.
                    getDocumentElement()),
                    new StreamResult(out));
            String xml = out.getBuffer().toString();
            if(LOGGER.isTraceEnabled())
            {
                LOGGER.trace("Sub tree output:");
                LOGGER.trace(xml);
            }

            return sourceDoc;
        }

        enum Op {
            DELETE_WITH_VALUE,
            DELETE_WITH_NAME,
            REPLACE_WITH_VALUE,
            SET_NAMESPACE,
            SORT_NODE_FIRST,
            COLLECT_OBJECT_IDS
        };

        private void logReceivedData(BufferedInputStream input)
                throws IOException {
            if(LOGGER.isTraceEnabled())
            {
                //Log input from device
                input.mark(Integer.MAX_VALUE);
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        input));
                StringBuilder output = new StringBuilder();
                String out;
                while (( out = br.readLine()) != null)
                {
                    output.append(out);
                }
                LOGGER.trace(output.toString());
                input.reset();
            }
        }

        /**
         * Utility routine that recursively traverses a node tree and
         * performs operations on nodes that match a criteria.
         *
         * @param node   - current top node
         * @param op     - operations to perform
         * @param name   - name (match criteria)
         * @param value  - value (match criteria)
         * @param list   - array list (used when collecting objectIds)
         */
        private void
        modifyConfig(Node node, Op op, String name, String value,
                     Map<Integer ,ArrayList< NodeInfo>> list) {


            NodeList nodeList = node.getChildNodes();
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node currentNode = nodeList.item(i);
                modifyConfig(currentNode, op, name, value, list);
            }

            String content = node.getTextContent();

            switch (op) {
            case DELETE_WITH_NAME:

                /*
                 * Delete node with node name matching
                 * the name argument. Child nodes are
                 * also deleted.
                 */
                if (node.getNodeName().equals(name)) {
                    LOGGER.debug("DELETE NODE WITH NAME :: "
                        + node.getParentNode().getNodeName()
                        + "/" + node.getNodeName());

                    node.getParentNode().removeChild(node);
                }
                break;

            case DELETE_WITH_VALUE:

                /*
                 * Delete node with node content matching
                 * the value argument.
                 */
                if (value != null
                && content != null
                && content.equals(value)) {
                    LOGGER.trace("DELETE NODE WITH VALUE :: "
                        + node.getParentNode().getNodeName()
                        + "/" + node.getNodeName()
                        + "=" + node.getTextContent());

                    node.getParentNode().removeChild(node);
                }
                break;

            case REPLACE_WITH_VALUE:

                /*
                 * If node name equals name argument then
                 * replace node content with value argument.
                 */
                if (content != null && content.matches(name)) {
                    LOGGER.debug("REPLACE NODE WITH VALUE :: "
                        + node.getParentNode().getNodeName()
                        + "/" + node.getNodeName()
                        + "=" + node.getTextContent()
                        + "->" + value);

                    node.setTextContent(node.getTextContent().
                                        replaceAll(name, value));

                }
                break;

            case SET_NAMESPACE:

                /*
                 * If node name equals name argument then
                 * set namespace prefix and uri on it.
                 */
                if (node.getNodeName().equals(name)) {
                    LOGGER.debug("SET NAMESPACE :: "
                        + node.getParentNode().getNodeName()
                        + "/" + node.getNodeName());

                    node.getOwnerDocument().renameNode(node,
                                                       value,
                                                       node.getNodeName());
                }
                break;

            case SORT_NODE_FIRST:

                /*
                 * If node name matches name argument
                 * then sort it as first child of the parent.
                 */
                if (node.getNodeName().equals(name)) {
                    Node parent = node.getParentNode();
                    parent.insertBefore(node,
                            parent.getFirstChild());

                    if(LOGGER.isTraceEnabled())
                        LOGGER.trace("SORT NODE FIRST:: "+ node.getNodeName());

                }
                break;

            case COLLECT_OBJECT_IDS:

                /*
                 * If node name matches "dn" then
                 * append the value, the node and position to the map.
                 * The map is has position as key.
                 */
                if (node.getNodeName().equals("dn")) {
                    String id = node.getTextContent();
                    if(LOGGER.isTraceEnabled())
                        LOGGER.trace("COLLECT OBJECT ID :: =" + id);
                    //position is the level in the object tree.
                    //Example: uni/tn-Test-NED-1/ap-AP-ncs-1
                    //means it on level 2, one below tenant.
                    //Sometimes the id contains a link in the end like:
                    //uni/tn-Test-NED-1/cont/
                    //               item-[uni/tn-Test-NED-1/ap-AP-ncs-1]
                    //Only counting '/' before the first '[' in the string.
                    int position = id.split("\\[")[0].split("/").length-1;
                    LOGGER.trace(id+"    "+position);
                    if(!list.containsKey(position))
                    {
                        list.put(position, new ArrayList<NodeInfo>());
                    }
                    list.get(position)
                        .add(new NodeInfo(node.getParentNode(),id));
                }
            default:
                break;
            }
        }

        private void sortNodes(HashMap<Integer, ArrayList< NodeInfo>> nodeMap) {
            for (ArrayList< NodeInfo> nodeList : nodeMap.values()) {
                if (nodeList != null) {
                    Collections.sort(nodeList,
                            new Comparator<NodeInfo>() {
                                @Override
                                public int compare(NodeInfo node1,
                                                   NodeInfo node2) {
                                    return node1.id.compareTo(node2.id);
                                }
                            });

                    // Reverse order, the nodes are inserted in the
                    // destination doc in reverse order, so we want
                    // to start with the last node.
                    Collections.reverse(nodeList);

                    for (NodeInfo subTree : nodeList) {
                        Node node = subTree.node;
                        Node parent = node.getParentNode();
                        parent.removeChild(node);

                        Node firstChild = parent.getFirstChild();
                        if (firstChild == null) {
                            //Only node in document
                            parent.appendChild(node);
                        } else {
                            parent.insertBefore(node, firstChild);
                        }
                    }
                }
            }
        }

        private String httpGet(String uri)
        {
            InputStream in ;
            HttpGet httpGet = new HttpGet( uri);

            LOGGER.debug("httpGet: "+uri);
            try {
                HttpResponse response = client.execute(httpGet);
                LOGGER.debug("response:\n"+response.toString());
                if(response.getStatusLine().getStatusCode()!=200)
                {
                    throw new RuntimeException(response.getStatusLine()
                            .toString());
                }
                in=response.getEntity().getContent();
                String body = IOUtils.toString(in);
//                LOGGER.debug("body:\n"+body);
                return body;
            } catch (ClientProtocolException e) {
                //TODO
                LOGGER.error(e);
            } catch (IOException e) {
                //TODO
                LOGGER.error(e);
            }
            return "";
        }
        /*
         * Get the sub tree under path.
         * Returned as an XML document
         */
        private Document getSubTree(String path)
        {
            Document subTree = null;
            String urlString = baseUrl+"/api/mo/"+path+".xml";
            String query = "query-target=subtree";
            try {
                LOGGER.debug("query: "+urlString+"?"+query);

            String response = httpGet(urlString+"?"+query);
            ByteArrayInputStream input =
                    new ByteArrayInputStream(response.getBytes());

            if(LOGGER.isTraceEnabled())
            {
                //Log input from device
                input.mark(Integer.MAX_VALUE);
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        input));
                StringBuilder output = new StringBuilder();
                String out;
                while (( out = br.readLine()) != null)
                {
                    output.append(out);
                }
                LOGGER.trace(output.toString());
                input.reset();
            }

            //Convert input from device to ncs form
            subTree = getSubTreeConfig(input);
            } catch (MalformedURLException e) {
                LOGGER.error(e);
            } catch (IOException e) {
                LOGGER.error(e);
            } catch (ParserConfigurationException e) {
                LOGGER.error(e);
            } catch (SAXException e) {
                LOGGER.error(e);
            } catch (TransformerException e) {
                LOGGER.error(e);
            }

            return subTree;
        }
    }
