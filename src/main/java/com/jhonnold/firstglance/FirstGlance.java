// https://medium.com/@a.lurie_78598/using-graph-theory-to-decide-where-to-start-reading-source-code-74a1e2ddf72

package com.jhonnold.firstglance;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirstGlance {
    public static void main(String[] args) throws Exception {
        FileRepositoryBuilder fileRepositoryBuilder = new FileRepositoryBuilder();
        Repository repository = fileRepositoryBuilder
                .setGitDir(new File(args[0]))
                .readEnvironment()
                .findGitDir()
                .setMustExist(true)
                .build();

        RevWalk walk = new RevWalk(repository);
        RevCommit headCommit = walk.parseCommit(repository.findRef(Constants.HEAD).getObjectId());
        walk.markStart(headCommit);

        List<String> files = getFilesInRepository(repository, headCommit);

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
            List<String> filesChanged = getChangedFilesInCommit(repository, commit);

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
//            System.out.printf("%s -- %1f\n", edge, graph.getEdgeWeight(edge));
        }

        for (String f: files) {
            for (String g : files) {
                if (!f.equals(g)) {
                    GraphPath<String, DefaultWeightedEdge> path = DijkstraShortestPath.findPathBetween(graph, f, g);

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

    private static List<String> getFilesInRepository(Repository repository, RevCommit head) throws Exception {
        List<String> filesInRepository = new ArrayList<>();

        TreeWalk treeWalk = new TreeWalk(repository);
        treeWalk.reset();
        treeWalk.setRecursive(true);
        treeWalk.addTree(head.getTree());

        while (treeWalk.next()) {
            if (treeWalk.getPathString().indexOf("test") < 0) {
                filesInRepository.add(treeWalk.getPathString());
            }
        }

        return filesInRepository;
    }

    private static List<String> getChangedFilesInCommit(Repository repository, RevCommit commit) {
        List<String> fileNamesInCommit = new ArrayList<>();
        RevWalk walk = new RevWalk(repository);

        try {
            // This is essentially the initial commit, we just add everything in the tree
            if (commit.getParentCount() == 0) {
                TreeWalk tw = new TreeWalk(repository);
                tw.reset();
                tw.setRecursive(true);
                tw.addTree(commit.getTree());

                while (tw.next()) {
                    fileNamesInCommit.add(tw.getPathString());
                }

                tw.close();
            } else {
                RevCommit parent = walk.parseCommit(commit.getParent(0).getId());

                DiffFormatter df = new DiffFormatter(NullOutputStream.INSTANCE);
                df.setRepository(repository);
                df.setDiffComparator(RawTextComparator.DEFAULT);
                df.setDetectRenames(true);

                List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
                for (DiffEntry diff : diffs) {
                    DiffEntry.ChangeType changeType = diff.getChangeType();
                    if (changeType.equals(DiffEntry.ChangeType.DELETE)) {
                        fileNamesInCommit.add(diff.getOldPath());
                    } else {
                        fileNamesInCommit.add(diff.getNewPath());
                    }
                }
            }
        } catch (Throwable t) {
        } finally {
            walk.dispose();
        }
        return fileNamesInCommit;
    }
}
