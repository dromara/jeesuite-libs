/*
 * Copyright 2016-2020 dromara.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dromara.mendmix.cos.provider.huawei;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import org.dromara.mendmix.common.MendmixBaseException;
import org.dromara.mendmix.common.util.JsonUtils;
import org.dromara.mendmix.cos.BucketConfig;
import org.dromara.mendmix.cos.CObjectMetadata;
import org.dromara.mendmix.cos.CUploadObject;
import org.dromara.mendmix.cos.CUploadResult;
import org.dromara.mendmix.cos.CosProviderConfig;
import org.dromara.mendmix.cos.UploadTokenParam;
import org.dromara.mendmix.cos.provider.AbstractProvider;
import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;
import com.obs.services.model.AccessControlList;
import com.obs.services.model.CreateBucketRequest;
import com.obs.services.model.DeleteObjectRequest;
import com.obs.services.model.DeleteObjectResult;
import com.obs.services.model.GrantAndPermission;
import com.obs.services.model.HttpMethodEnum;
import com.obs.services.model.ObjectListing;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsBucket;
import com.obs.services.model.ObsObject;
import com.obs.services.model.Permission;
import com.obs.services.model.PutObjectResult;
import com.obs.services.model.TemporarySignatureRequest;
import com.obs.services.model.TemporarySignatureResponse;
import com.qcloud.cos.utils.IOUtils;

public class HuaweicloudProvider extends AbstractProvider {

	public static final String NAME = "huaweicloud";

	private static Logger logger = LoggerFactory.getLogger(HuaweicloudProvider.class);
	private ObsClient obsClient;

	public HuaweicloudProvider(CosProviderConfig conf) {
		super(conf);
		if (StringUtils.isBlank(conf.getRegionName())) {
			conf.setRegionName("cn-south-1");
		}
		String endpoint = conf.getEndpoint();
		if (endpoint == null) {
			endpoint = String.format("https://obs.%s.myhuaweicloud.com", conf.getRegionName());
		}
		ObsConfiguration obsConfiguration = new ObsConfiguration();
		obsConfiguration.setEndPoint(endpoint);
		obsConfiguration.setSocketTimeout(30000);
		obsConfiguration.setConnectionTimeout(5000);
		obsClient = new ObsClient(conf.getAccessKey(), conf.getSecretKey(), obsConfiguration);
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public boolean existsBucket(String bucketName) {
		boolean exists = obsClient.headBucket(bucketName);
		return exists;
	}

	@Override
	public void createBucket(String bucketName, boolean isPrivate) {
		if (existsBucket(bucketName)) {
			throw new MendmixBaseException("bucket[" + bucketName + "] 已经存在");
		}
		CreateBucketRequest request = new CreateBucketRequest(bucketName, conf.getRegionName());
		ObsBucket bucket = new ObsBucket();
		bucket.setBucketName(bucketName);
		AccessControlList acl = null;
		if (isPrivate) {
			acl = AccessControlList.REST_CANNED_PRIVATE;
		} else {
			acl = AccessControlList.REST_CANNED_PUBLIC_READ;
		}
		request.setAcl(acl);
		obsClient.createBucket(request);
	}

	@Override
	public void deleteBucket(String bucketName) {
		if (!existsBucket(bucketName)) {
			logger.info("MENDMIX-TRACE-LOGGGING-->> Bucket[{}]不存在", bucketName);
			return;
		}
		ObjectListing objectListing = obsClient.listObjects(bucketName);
		if (objectListing != null && !objectListing.getObjects().isEmpty()) {
			logger.error("MENDMIX-TRACE-LOGGGING-->> 桶[{}]不为空， 不能删除", bucketName);
			throw new MendmixBaseException("桶[" + bucketName + "]不为空， 不能删除");
		}
		obsClient.deleteBucket(bucketName);
	}

	@Override
	public BucketConfig getBucketConfig(String bucketName) {
		if (!existsBucket(bucketName)) {
			logger.info("MENDMIX-TRACE-LOGGGING-->> Bucket[{}]不存在", bucketName);
			return null;
		}
		boolean isPublic = false;
		AccessControlList acl = obsClient.getBucketAcl(bucketName);
		Set<GrantAndPermission> grants = acl.getGrants();
		isPublic = grants.stream().anyMatch(p -> Permission.PERMISSION_FULL_CONTROL.equals(p.getPermission())
				|| Permission.PERMISSION_READ.equals(p.getPermission()));
		return new BucketConfig(bucketName, !isPublic, null);
	}

	@Override
	public CUploadResult upload(CUploadObject object) {
		String bucketName = object.getBucketName();
		if (StringUtils.isBlank(bucketName)) {
			throw new MendmixBaseException("BucketName 不能为空");
		}
		InputStream inputStream = object.getInputStream();
		File file = object.getFile();
		String fileKey = object.getFileKey();
		byte[] bytes = object.getBytes();
		long size = 0;
		PutObjectResult result = null;
		ObjectMetadata metadata = new ObjectMetadata();
		if (object.getMimeType() != null) {
			metadata.setContentType(object.getMimeType());
		}
		if (file != null) {
			result = obsClient.putObject(bucketName, fileKey, file, metadata);
			size = file.length();
		} else if (bytes != null) {
			inputStream = new ByteArrayInputStream(bytes);
			result = obsClient.putObject(bucketName, fileKey, inputStream, metadata);
			size = bytes.length;
		} else if (inputStream != null) {
			result = obsClient.putObject(bucketName, fileKey, inputStream, metadata);
		} else {
			throw new MendmixBaseException("upload object is NULL");
		}
		if (result.getStatusCode() == 200) {
			CUploadResult uploadResult = new CUploadResult();
			uploadResult.setFileKey(fileKey);
			uploadResult.setFileUrl(getDownloadUrl(bucketName, fileKey, 3600));
			uploadResult.setMimeType(object.getMimeType());
			uploadResult.setFileSize(size);
			if (size == 0 || StringUtils.isBlank(object.getMimeType())) {
				metadata = obsClient.getObjectMetadata(bucketName, result.getObjectKey());
				uploadResult.setMimeType(metadata.getContentType());
				uploadResult.setFileSize(metadata.getContentLength());
			}
			return uploadResult;
		} else {
			logger.warn("UPLOAD_ERROR:{}", JsonUtils.toJson(result));
			throw new MendmixBaseException("上传失败");
		}
	}

	@Override
	public boolean exists(String bucketName, String fileKey) {
		if (!existsBucket(bucketName)) {
			return false;
		}
		ObsObject object = null;
		try {
			object = obsClient.getObject(bucketName, fileKey);
		} catch (Exception e) {
		}
		return object != null;
	}

	@Override
	public boolean delete(String bucketName, String fileKey) {
		if (!exists(bucketName, fileKey)) {
			return false;
		}
		DeleteObjectRequest request = new DeleteObjectRequest();
		request.setBucketName(bucketName);
		request.setObjectKey(fileKey);
		DeleteObjectResult result = obsClient.deleteObject(request);
		return result.isDeleteMarker();
	}

	@Override
	public byte[] getObjectBytes(String bucketName, String fileKey) {
		if (!existsBucket(bucketName)) {
			logger.info("MENDMIX-TRACE-LOGGGING-->> Bucket[{}]不存在", bucketName);
			return null;
		}
		try {
			ObsObject object = obsClient.getObject(bucketName, fileKey);
			InputStream inputStream = object.getObjectContent();
			byte[] bytes = IOUtils.toByteArray(inputStream);
			inputStream.close();
			return bytes;
		} catch (Exception e) {
			logger.error("MENDMIX-TRACE-LOGGGING-->> 获取字节, bucketName={}, fileKey={}, e={}", bucketName, fileKey,
					ExceptionUtils.getMessage(e));
		}
		return null;
	}

	@Override
	public InputStream getObjectInputStream(String bucketName, String fileKey) {
		if (!existsBucket(bucketName)) {
			logger.info("MENDMIX-TRACE-LOGGGING-->> Bucket[{}]不存在", bucketName);
			return null;
		}
		try {
			ObsObject object = obsClient.getObject(bucketName, fileKey);
			InputStream inputStream = object.getObjectContent();
			return inputStream;
		} catch (Exception e) {
			logger.error("获取流失败, bucketName={}, fileKey={}, e={}", bucketName, fileKey, ExceptionUtils.getMessage(e));
			throw new MendmixBaseException(e.getMessage());
		}
	}

	@Override
	public Map<String, Object> createUploadToken(UploadTokenParam param) {
		return null;
	}

	@Override
	public CObjectMetadata getObjectMetadata(String bucketName, String fileKey) {
		ObjectMetadata objectMetadata = obsClient.getObjectMetadata(bucketName, fileKey);
		if (objectMetadata == null) {
			return null;
		}
		CObjectMetadata result = new CObjectMetadata();
		Map<String, Object> customMetadata = objectMetadata.getResponseHeaders();
		if (customMetadata != null) {
			Map<String, String> metadata = Maps.newHashMap();
			for (Map.Entry<String, Object> entry : customMetadata.entrySet()) {
				metadata.put(entry.getKey(), entry.getValue().toString());
			}
			result.setCustomMetadatas(metadata);
		}
		result.setMimeType(objectMetadata.getContentType());
		result.setFilesize(objectMetadata.getContentLength());

		return result;
	}

	@Override
	public void close() {
		try {
			if (obsClient != null) {
				obsClient.close();
			}
		} catch (Exception e) {
			logger.error("MENDMIX-TRACE-LOGGGING-->> obsClient关闭失败, e={}", ExceptionUtils.getMessage(e));
		}
	}

	@Override
	protected String buildBucketUrlPrefix(String bucketName) {
		// mendmix.obs.cn-south-1.myhuaweicloud.com
		return String.format("https://%s.obs.%s.myhuaweicloud.com", bucketName, conf.getRegionName());
	}

	@Override
	protected String generatePresignedUrl(String bucketName, String fileKey, int expireInSeconds) {
		// 默认5分钟， 最长7天
		if (!exists(bucketName, fileKey)) {
			throw new MendmixBaseException("对象[bucketName=" + bucketName + ",fileKey=" + fileKey + "]不存在");
		}
		TemporarySignatureRequest req = new TemporarySignatureRequest(HttpMethodEnum.GET, expireInSeconds);
		req.setBucketName(bucketName);
		req.setObjectKey(fileKey);
		TemporarySignatureResponse res = obsClient.createTemporarySignature(req);
		String signedUrl = res.getSignedUrl();
		return signedUrl;
	}

}
