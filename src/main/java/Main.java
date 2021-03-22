import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.json.JSONObject;

public class Main {

    private final String serverUri =
        "https://n35ro2ic4d.execute-api.eu-central-1.amazonaws.com/prod/engine-rest/process-definition/key/invoice/xml";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final HttpRequest httpRequest = HttpRequest.newBuilder().uri( URI.create( serverUri ) ).build();

    public static void main(String[] args) {
        if ( args.length == 2 ) {
            final String startNodeId = args[0];
            final String targetNodeId = args[1];

            Main mainProgram = new Main();
            mainProgram.printIdsForOnePossiblePath( startNodeId, targetNodeId );
        }
    }

    private void printIdsForOnePossiblePath(final String startNodeId, final String targetNodeId) {
        try {
            Optional.ofNullable( httpClient.send( httpRequest, HttpResponse.BodyHandlers.ofString() ) )
                .ifPresent( httpResponse -> {

                    BpmnModelInstance bpmnModel = Bpmn.readModelFromStream( getXmlAsInputStream( httpResponse ) );
                    FlowNode startFlowNode = bpmnModel.getModelElementById( startNodeId );
                    var shortestPathNodeIdList = findShortestPath(
                        new ArrayList<>(),
                        startFlowNode,
                        targetNodeId
                    );

                    if ( shortestPathNodeIdList.isEmpty() ) {
                        System.exit( -1 );
                    }
                    System.out.printf( "The path from %s to %s is:%n", startNodeId, targetNodeId );
                    System.out.println( shortestPathNodeIdList );
                } );
        }
        catch ( IOException | InterruptedException e ) {
            System.exit( -1 );
        }
    }

    private InputStream getXmlAsInputStream(HttpResponse<String> httpResponse) {
        // assume that response will always be a valid json structure
        JSONObject jsonObject = new JSONObject( httpResponse.body() );
        String xmlString = jsonObject.getString( "bpmn20Xml" );

        return new ByteArrayInputStream( xmlString.getBytes() );
    }

    private List<String> findShortestPath(final List<String> visitedNodeIds,
                                          final FlowNode currentNode,
                                          final String targetNodeId) {
        if ( currentNode.getOutgoing() == null ) {
            return Collections.emptyList();
        }
        visitedNodeIds.add( currentNode.getId() );

        for ( SequenceFlow outgoingEdge : currentNode.getOutgoing() ) {
            final String outgoingTargetNodeId = outgoingEdge.getTarget().getId();

            if ( outgoingTargetNodeId.equals( targetNodeId ) ) {
                // Target node found
                return new ArrayList<>() {{
                    add( currentNode.getId() );
                    add( targetNodeId );
                }};
            }
            // Avoid already visited out-going nodes
            if ( !visitedNodeIds.contains( outgoingTargetNodeId ) ) {
                var listOfIdsFromChildNode = findShortestPath(
                    visitedNodeIds,
                    outgoingEdge.getTarget(),
                    targetNodeId
                );

                if ( !listOfIdsFromChildNode.isEmpty() ) {
                    listOfIdsFromChildNode.add( 0, currentNode.getId() );
                    return listOfIdsFromChildNode;
                }
            }
        }
        // currFlowNode is not directing to target node
        return Collections.emptyList();
    }
}
