package com.strandls.observation.pojo;

public class TopUploadersInfo {

	private String name;
	private String pic;
	private Long authorId;
	private Long count;

	public TopUploadersInfo(String name, String pic, Long authorId, Long count) {
		super();
		this.name = name;
		this.pic = pic;
		this.authorId = authorId;
		this.count = count;
	}

	public TopUploadersInfo() {
		super();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPic() {
		return pic;
	}

	public void setPic(String pic) {
		this.pic = pic;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public void setAuthorId(Long authorId) {
		this.authorId = authorId;
	}

	public Long getCount() {
		return count;
	}

	public void setCount(Long count) {
		this.count = count;
	}

}
