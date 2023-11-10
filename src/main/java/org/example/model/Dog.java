package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Dog extends Animal {
	@JsonProperty("bark_volume")
	private int barkVolume;

	public String getBreed() {
		return "labrador";
	}
}
