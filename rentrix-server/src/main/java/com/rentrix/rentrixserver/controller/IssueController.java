package com.rentrix.rentrixserver.controller;

import com.rentrix.rentrixserver.entity.Issue;
import com.rentrix.rentrixserver.service.impl.IssueServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/issues")
public class IssueController {
	
	private final IssueServiceImpl issueServiceImpl;
	
	@Autowired
	public IssueController(IssueServiceImpl issueServiceImpl) {
		this.issueServiceImpl = issueServiceImpl;
	}
	
	@GetMapping
	public List<Issue> getAllIssues() {
		return issueServiceImpl.getAllIssues();
	}
	
	@GetMapping("/{id}")
	public ResponseEntity<Issue> getIssueById(@PathVariable Long id) {
		Issue issue = issueServiceImpl.getIssueById(id);
		return issue != null ? ResponseEntity.ok(issue) : ResponseEntity.notFound().build();
	}
	
	@PostMapping
	public ResponseEntity<Issue> createIssue(@RequestBody Issue issue) {
		Issue savedIssue = issueServiceImpl.saveIssue(issue);
		return ResponseEntity.status(201).body(savedIssue);
	}
	
	@PutMapping("/{id}")
	public ResponseEntity<Issue> updateIssue(@PathVariable Long id, @RequestBody Issue issue) {
		Issue existingIssue = issueServiceImpl.getIssueById(id);
		if (existingIssue != null) {
			issue.setId(id);
			Issue updatedIssue = issueServiceImpl.saveIssue(issue);
			return ResponseEntity.ok(updatedIssue);
		} else {
			return ResponseEntity.notFound().build();
		}
	}
	
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteIssue(@PathVariable Long id) {
		issueServiceImpl.deleteIssue(id);
		return ResponseEntity.noContent().build();
	}
	
}