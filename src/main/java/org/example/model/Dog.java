package org.example.model;

import lombok.Data;

@Data
public class Dog extends Animal {
	private int barkVolume;

	public String getBreed() {
		return "labrador";
	}
}
