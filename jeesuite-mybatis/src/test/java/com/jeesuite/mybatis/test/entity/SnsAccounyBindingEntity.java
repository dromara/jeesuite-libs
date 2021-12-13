package com.jeesuite.mybatis.test.entity;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Table(name = "sns_account_binding")
public class SnsAccounyBindingEntity extends TestBaseEntity {
	
	public static enum SnsType{
		weixin,weibo,qq
	}
	
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id",updatable = false)
    private Integer userId;

    @Column(name = "sns_type",updatable = false)
    private String snsType;

    @Column(name = "union_id")
    private String unionId;

    @Column(name = "open_id",updatable = false)
    private String openId;

    private Boolean enabled = true;
    
    /**
     * @return id
     */
    public Integer getId() {
        return id;
    }

    /**
     * @param id
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * @return user_id
     */
    public Integer getUserId() {
        return userId;
    }

    /**
     * @param userId
     */
    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    /**
     * @return sns_type
     */
    public String getSnsType() {
        return snsType;
    }

    /**
     * @param snsType
     */
    public void setSnsType(String snsType) {
        this.snsType = snsType;
    }

    /**
     * @return union_id
     */
    public String getUnionId() {
        return unionId;
    }

    /**
     * @param unionId
     */
    public void setUnionId(String unionId) {
        this.unionId = unionId;
    }

    /**
     * @return open_id
     */
    public String getOpenId() {
        return openId;
    }

    /**
     * @param openId
     */
    public void setOpenId(String openId) {
        this.openId = openId;
    }

    /**
     * @return enabled
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * @param enabled
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

}