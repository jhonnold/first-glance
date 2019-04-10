package com.jhonnold.firstglance;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.NullOutputStream;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RepositoryHelper {
    private final String repositoryFolderPath;
    private Repository repository;
    private RevCommit headCommit;

    public RepositoryHelper() {
        this("./.git");
    }

    public RepositoryHelper(String repoFolderPath) {
        this.repositoryFolderPath = repoFolderPath;
    }
    
    public Repository getRepository() {
        return this.repository;
    }
    
    public RevCommit getHeadCommit() {
        return this.headCommit;
    }

    public void construct() throws IOException {
        FileRepositoryBuilder fileRepositoryBuilder = new FileRepositoryBuilder();

        this.repository = fileRepositoryBuilder
                .setGitDir(new File(this.repositoryFolderPath))
                .readEnvironment()
                .findGitDir()
                .setMustExist(true)
                .build();

        RevWalk revWalk = new RevWalk(this.repository);
        Ref headRef = this.repository.findRef(Constants.HEAD);
        this.headCommit = revWalk.parseCommit(headRef.getObjectId());

        revWalk.dispose();
    }

    public List<String> getFilesInCommit(RevCommit commit) throws IOException {
        List<String> filesInCommit = new ArrayList<>();

        TreeWalk treeWalk = new TreeWalk(this.repository);
        treeWalk.reset();
        treeWalk.setRecursive(true);
        treeWalk.addTree(commit.getTree());

        while (treeWalk.next()) {
            filesInCommit.add(treeWalk.getPathString());
        }

        return filesInCommit;
    }

    public List<String> getChangedFilesInCommit(RevCommit commit) throws IOException {
        if (commit.getParentCount() == 0) {
            return this.getFilesInCommit(commit);
        }

        DiffFormatter diffFormatter = new DiffFormatter(NullOutputStream.INSTANCE);
        diffFormatter.setRepository(repository);
        diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
        diffFormatter.setDetectRenames(true);

        RevCommit parent = commit.getParent(0);
        List<DiffEntry> diffs = diffFormatter.scan(parent.getTree(), commit.getTree());

        return diffs.stream()
                .map(DiffEntry::getNewPath)
                .collect(Collectors.toList());
    }

    public List<String> getFilesInHead() throws IOException {
        return this.getFilesInCommit(this.headCommit);
    }
}
