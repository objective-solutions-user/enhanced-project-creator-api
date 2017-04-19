package br.objective.jira.rest;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;

public class ProjectBuilderResponse implements Serializable{
	private static final long serialVersionUID = 2643704078232781352L;
	public boolean success = true;
	public Long idOfCreatedProject= null;
	public List<String> errors = new LinkedList<>();
	public List<String> warnings = new LinkedList<String>();

	public ProjectBuilderResponse withError(String errorMessage) {
		success = false;
		errors.add(errorMessage);
		return this;
	}

	public ProjectBuilderResponse withError(String errorMessage, Exception e) {
		success = false;
		String exceptionMessage = e.getMessage();
		Throwable cause = e.getCause();
		while (cause != null) {
			exceptionMessage+=" causedBy: " + cause.getMessage();
			cause = cause.getCause();
		}

		errors.add(errorMessage + ". " + exceptionMessage);
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		errors.add("Stacktrace: \n"+sw.toString());
		return this;
	}
	
	public void addWarnings(List<String> warnings) {
		this.warnings.addAll(warnings);
	}
}