package com.malt.mongopostgresqlstreamer.monitoring;

import java.util.Date;

import lombok.Data;

@Data
public class InitialImport {
	private Double lengthInMinutes;
	private Date start;
	private Date end;
	private String status;
}
