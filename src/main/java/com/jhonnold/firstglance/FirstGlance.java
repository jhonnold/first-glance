// https://medium.com/@a.lurie_78598/using-graph-theory-to-decide-where-to-start-reading-source-code-74a1e2ddf72

package com.jhonnold.firstglance;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirstGlance {
    public static void main(String[] args) throws Exception {
        RepositoryHelper repositoryHelper = new RepositoryHelper(args[0]);

        try {
            repositoryHelper.construct();
        } catch (IOException e) {
            System.err.printf("Failed to construct Repository Helper: %s\n", e.getMessage());
        }

        RevWalk walk = new RevWalk(repositoryHelper.getRepository());
        walk.markStart(repositoryHelper.getHeadCommit());

        List<String> files = repositoryHelper.getFilesInHead();

        Graph<String, DefaultWeightedEdge> graph = new DefaultUndirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Map<String, Integer> fileWeights = new HashMap<>();

        files.forEach(graph::addVertex);
        files.forEach(f -> fileWeights.put(f, 0));

        for (String f : files) {
            for (String g : files) {
                if (!f.equals(g)) graph.addEdge(f, g);
            }
        }

        for (RevCommit commit : walk) {
            List<String> filesChanged = repositoryHelper.getChangedFilesInCommit(commit);

            for (String f : filesChanged) {
                for (String g : filesChanged) {
                    DefaultWeightedEdge edge = graph.getEdge(f, g);
                    if (edge == null) continue;

                    graph.setEdgeWeight(f, g, graph.getEdgeWeight(edge) + 1);
                }
            }
        }

        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            graph.setEdgeWeight(edge, 1 / graph.getEdgeWeight(edge));
        }

        FloydWarshallShortestPaths<String, DefaultWeightedEdge> shortestPaths = new FloydWarshallShortestPaths<>(graph);

        for (String f: files) {
            for (String g : files) {
                if (!f.equals(g)) {
                    GraphPath<String, DefaultWeightedEdge> path = shortestPaths.getPath(f, g);

                    for (String p : path.getVertexList()) {
                        if (!p.equals(f) && !p.equals(g)) fileWeights.put(p, fileWeights.get(p) + 1);
                    }
                }
            }
        }

        files.sort((a, b) -> fileWeights.get(b) - fileWeights.get(a));

        for (String f : files) {
            System.out.printf("%s -- %d\n", f, fileWeights.get(f));
        }
    }
}
