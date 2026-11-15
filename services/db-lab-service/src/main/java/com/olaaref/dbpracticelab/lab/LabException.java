package com.olaaref.dbpracticelab.lab;

/** A problem the learner can understand (unknown lab, server down, bad request); shown in the UI. */
public class LabException extends RuntimeException {
  private final int status;

  public LabException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
