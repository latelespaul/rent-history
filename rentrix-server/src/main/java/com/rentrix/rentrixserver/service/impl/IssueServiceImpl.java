package com.rentrix.rentrixserver.service.impl;

import com.rentrix.rentrixserver.entity.Issue;
import com.rentrix.rentrixserver.repository.IssueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IssueServiceImpl {
	
	private final IssueRepository issueRepository;
	
	@Autowired
	public IssueServiceImpl(IssueRepository issueRepository) {
		this.issueRepository = issueRepository;
	}
	
	public List<Issue> getAllIssues() {
		return issueRepository.findAll();
	}
	
	public Issue getIssueById(Long id) {
		return issueRepository.findById(id).orElse(null);
	}
	
	public Issue saveIssue(Issue issue) {
		return issueRepository.save(issue);
	}
	
	public void deleteIssue(Long id) {
		issueRepository.deleteById(id);
	}
	
}