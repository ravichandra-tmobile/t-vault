package com.tmobile.cso.vault.api.model;

import java.io.Serializable;

import io.swagger.annotations.ApiModelProperty;

public class AWSLoginRole implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7895793207885356750L;
	
	private String auth_type;
	private String role;
	private String bound_ami_id;
	private String bound_account_id;
	private String bound_region;
	private String bound_vpc_id;
	private String bound_subnet_id;
	private String bound_iam_role_arn;
	private String bound_iam_instance_profile_arn;
	private String policies;
	
	public AWSLoginRole() {
		// TODO Auto-generated constructor stub
	}

	public AWSLoginRole(String auth_type, String role, String bound_ami_id, String bound_account_id,
			String bound_region, String bound_vpc_id, String bound_subnet_id, String bound_iam_role_arn,
			String bound_iam_instance_profile_arn, String policies) {
		super();
		this.auth_type = auth_type;
		this.role = role;
		this.bound_ami_id = bound_ami_id;
		this.bound_account_id = bound_account_id;
		this.bound_region = bound_region;
		this.bound_vpc_id = bound_vpc_id;
		this.bound_subnet_id = bound_subnet_id;
		this.bound_iam_role_arn = bound_iam_role_arn;
		this.bound_iam_instance_profile_arn = bound_iam_instance_profile_arn;
		this.policies = policies;
	}

	/**
	 * @return the auth_type
	 */
	@ApiModelProperty(example="ec2", position=1)
	public String getAuth_type() {
		return auth_type;
	}

	/**
	 * @return the role
	 */
	@ApiModelProperty( position=2)
	public String getRole() {
		return role;
	}

	/**
	 * @return the bound_ami_id
	 */
	@ApiModelProperty(position=3)
	public String getBound_ami_id() {
		return bound_ami_id;
	}

	/**
	 * @return the bound_account_id
	 */
	@ApiModelProperty(position=4)
	public String getBound_account_id() {
		return bound_account_id;
	}

	/**
	 * @return the bound_region
	 */
	@ApiModelProperty( position=5)
	public String getBound_region() {
		return bound_region;
	}

	/**
	 * @return the bound_vpc_id
	 */
	@ApiModelProperty( position=6)
	public String getBound_vpc_id() {
		return bound_vpc_id;
	}

	/**
	 * @return the bound_subnet_id
	 */
	@ApiModelProperty(position=7)
	public String getBound_subnet_id() {
		return bound_subnet_id;
	}

	/**
	 * @return the bound_iam_role_arn
	 */
	@ApiModelProperty( position=8)
	public String getBound_iam_role_arn() {
		return bound_iam_role_arn;
	}

	/**
	 * @return the bound_iam_instance_profile_arn
	 */
	@ApiModelProperty( position=9)
	public String getBound_iam_instance_profile_arn() {
		return bound_iam_instance_profile_arn;
	}

	/**
	 * @return the policies
	 */
	@ApiModelProperty(example="", position=10)
	public String getPolicies() {
		return policies;
	}

	/**
	 * @param auth_type the auth_type to set
	 */
	public void setAuth_type(String auth_type) {
		this.auth_type = auth_type;
	}

	/**
	 * @param role the role to set
	 */
	public void setRole(String role) {
		this.role = role;
	}

	/**
	 * @param bound_ami_id the bound_ami_id to set
	 */
	public void setBound_ami_id(String bound_ami_id) {
		this.bound_ami_id = bound_ami_id;
	}

	/**
	 * @param bound_account_id the bound_account_id to set
	 */
	public void setBound_account_id(String bound_account_id) {
		this.bound_account_id = bound_account_id;
	}

	/**
	 * @param bound_region the bound_region to set
	 */
	public void setBound_region(String bound_region) {
		this.bound_region = bound_region;
	}

	/**
	 * @param bound_vpc_id the bound_vpc_id to set
	 */
	public void setBound_vpc_id(String bound_vpc_id) {
		this.bound_vpc_id = bound_vpc_id;
	}

	/**
	 * @param bound_subnet_id the bound_subnet_id to set
	 */
	public void setBound_subnet_id(String bound_subnet_id) {
		this.bound_subnet_id = bound_subnet_id;
	}

	/**
	 * @param bound_iam_role_arn the bound_iam_role_arn to set
	 */
	public void setBound_iam_role_arn(String bound_iam_role_arn) {
		this.bound_iam_role_arn = bound_iam_role_arn;
	}

	/**
	 * @param bound_iam_instance_profile_arn the bound_iam_instance_profile_arn to set
	 */
	public void setBound_iam_instance_profile_arn(String bound_iam_instance_profile_arn) {
		this.bound_iam_instance_profile_arn = bound_iam_instance_profile_arn;
	}

	/**
	 * @param policies the policies to set
	 */
	public void setPolicies(String policies) {
		this.policies = policies;
	}

}