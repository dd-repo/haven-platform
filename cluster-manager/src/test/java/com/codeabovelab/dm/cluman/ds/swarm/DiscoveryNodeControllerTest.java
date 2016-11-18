package com.codeabovelab.dm.cluman.ds.swarm;

import com.codeabovelab.dm.cluman.ds.nodes.NodeAgentData;
import com.codeabovelab.dm.cluman.ds.nodes.NodeStorage;
import com.codeabovelab.dm.cluman.model.*;
import com.codeabovelab.dm.cluman.ds.nodes.DiscoveryNodeController;

import static org.hamcrest.Matchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.NestedServletException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Test for swarm token discovery service implementation
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = DiscoveryNodeControllerTest.TestConfiguration.class)
public class DiscoveryNodeControllerTest {
    private static final String CLUSTER_ID = "cluster_id";
    private static final String URL = "/discovery/nodes";
    private static final String SECRET = "secr3t";

    @Configuration
    public static class TestConfiguration {
        final Map<String, Node> nodes = new HashMap<>();

        @Bean
        NodeStorage nodeStorage() {
            NodeStorage ns = mock(NodeStorage.class);
            Mockito.doAnswer(invocation -> {
                final NodeUpdate node = invocation.getArgumentAt(0, NodeUpdate.class);
                NodeInfo nodeInfo = node.getNode();
                nodes.put(nodeInfo.getAddress(), nodeInfo);
                return null;
            }).when(ns).updateNode(anyObject(), anyInt());
            return ns;
        }

        @Bean
        DiscoveryStorage discoveryStorage() {
            NodesGroup cluster = mock(NodesGroup.class);
            when(cluster.getNodes()).thenReturn(nodes.values());

            DiscoveryStorage storage = mock(DiscoveryStorage.class);

            when(storage.getCluster(CLUSTER_ID)).thenReturn(cluster);
            when(storage.getClusterForNode(anyString(), eq(CLUSTER_ID))).thenReturn(cluster);
            when(storage.getClusterForNode(eq(CLUSTER_ID), anyString())).thenReturn(cluster);
            return storage;
        }
    }

    @Autowired
    private DiscoveryStorage discoveryStorage;
    @Autowired
    private NodeStorage nodeStorage;
    private MockMvc mvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void before() {
        mvc = standaloneSetup(new DiscoveryNodeController(nodeStorage, SECRET))
          .build();
    }

    private RestTemplate restTemplate = new RestTemplate();

    @SuppressWarnings("unchecked")
    @Test
    public void test() throws Exception {
        final String clusterId = CLUSTER_ID;
        NodesGroup cluster = discoveryStorage.getCluster(clusterId);

        {
            Collection<Node> nodes = cluster.getNodes();
            assertThat(nodes, empty());
        }

        final String hostPort = "on.e:1234";
        final String secondHostPort = "two.e:134";
        addNode(hostPort, true);
        addNode(secondHostPort, true);
        try {
            addNode("unauthorized:876", false);
            fail("It unauthorized and must failed");
        } catch (NestedServletException e) {
            //fail as axpected
        }

        {
            Collection<Node> nodes = cluster.getNodes();
            assertThat(nodes, hasSize(2));
            System.out.println(nodes);
            assertThat(nodes, hasItems(hasProperty("address", is(hostPort)), hasProperty("address", is(secondHostPort))));
        }
    }

    @SuppressWarnings("deprecation")
    private void addNode(String hostPort, boolean auth) throws Exception {
        NodeAgentData data = new NodeAgentData();
        data.setName(hostPort);
        data.setAddress(hostPort);
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.post(getClusterUrl(data.getName()))
          .param("ttl", "234")
          .contentType(MimeTypeUtils.APPLICATION_JSON_VALUE)
          .content(objectMapper.writeValueAsString(data));
        if(auth) {
            b.header("X-Auth-Node", SECRET);
        }
        mvc.perform(b).andExpect(status().isOk());
    }

    private String getClusterUrl(String clusterId) {
        return URL + "/" + clusterId;
    }

}