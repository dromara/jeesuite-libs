/*
 * Copyright 2016-2020 www.jeesuite.com.
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
package org.dromara.mendmix.common.http;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.dromara.mendmix.common.CurrentRuntimeContext;
import org.dromara.mendmix.common.CustomRequestHeaders;
import org.dromara.mendmix.common.GlobalConstants;
import org.dromara.mendmix.common.GlobalContext;
import org.dromara.mendmix.common.MendmixBaseException;
import org.dromara.mendmix.common.crypt.Base64;
import org.dromara.mendmix.common.model.AuthUser;
import org.dromara.mendmix.common.util.BeanUtils;
import org.dromara.mendmix.common.util.HttpUtils;
import org.dromara.mendmix.common.util.JsonUtils;
import org.dromara.mendmix.common.util.MimeTypeUtils;
import org.dromara.mendmix.common.util.MimeTypeUtils.FileMeta;
import org.dromara.mendmix.common.util.ParameterUtils;
import org.dromara.mendmix.common.util.ResourceUtils;
import org.dromara.mendmix.common.util.TokenGenerator;


/**
 * 
 * 
 * <br>
 * Class Name : HttpRequestEntity
 *
 * @author <a href="mailto:vakinge@gmail.com">vakin</a>
 * @date 2017年8月24日
 */
public class HttpRequestEntity {

	public static final String HTTP_THREAD_NAME = "http-";
	private String uri;
	private HttpMethod method;
	private String charset;
	private String contentType;
	private Map<String, String> headers;
	private Map<String, Object> queryParams;
	private Map<String, Object> formParams;
	private String body;
	private BasicAuthParams basicAuth;
	
	private boolean logging = true;

	private boolean multipart;
	private String boundary;

	private boolean fallbackHitCache;

	private Proxy proxy;
	
	private HttpRequestEntity() {
	}

	public static HttpRequestEntity create(HttpMethod method) {
		return new HttpRequestEntity().method(method);
	}

	public static HttpRequestEntity post(String uri) {
		return new HttpRequestEntity().method(HttpMethod.POST).uri(uri);
	}

	public static HttpRequestEntity get(String uri) {
		return new HttpRequestEntity().method(HttpMethod.GET).uri(uri);
	}

	public String getCharset() {
		if (charset == null) {
			charset = parseContentTypeCharset(contentType);
		}
		return charset;
	}
	
	public HttpRequestEntity proxy(Proxy proxy) {
		this.proxy = proxy;
		return this;
	}

	public HttpRequestEntity fallbackHitCache() {
		this.fallbackHitCache = HttpClientProvider.fallbackEnabled;
		return this;
	}

	public HttpMethod getMethod() {
		return method;
	}

	public String getUri() {
		return uri;
	}

	public HttpRequestEntity uri(String uri) {
		this.uri = uri;
		return this;
	}
	
	
	public HttpRequestEntity logging(boolean logging) {
		this.logging = logging;
		return this;
	}
	
	public boolean isLogging() {
		return logging;
	}

	public HttpRequestEntity internalCall() {
		if(headers == null)headers = new HashMap<>();
		if (!headers.containsKey(CustomRequestHeaders.HEADER_INVOKE_TOKEN)) {
			header(CustomRequestHeaders.HEADER_INVOKE_TOKEN, TokenGenerator.generateWithSign());
		}
		return header(CustomRequestHeaders.HEADER_INTERNAL_REQUEST, Boolean.TRUE.toString());
	}

	public HttpRequestEntity backendInternalCall() {
		if(headers == null)headers = new HashMap<>();
		header(CustomRequestHeaders.HEADER_RESP_KEEP, Boolean.TRUE.toString());
		header(CustomRequestHeaders.HEADER_IGNORE_TENANT, Boolean.TRUE.toString());
		header(CustomRequestHeaders.HEADER_IGNORE_AUTH, Boolean.TRUE.toString());
		header(HttpHeaders.USER_AGENT, GlobalConstants.BACKEND_USER_AGENT_NAME);
		return internalCall();
	}

	public HttpRequestEntity useContext() {
		Map<String, String> contextHeaders = CurrentRuntimeContext.getContextHeaders();
		headers(contextHeaders);
		if (!getHeaders().containsKey(CustomRequestHeaders.HEADER_INVOKE_TOKEN)) {
			header(CustomRequestHeaders.HEADER_INVOKE_TOKEN, TokenGenerator.generateWithSign());
		}
		AuthUser currentUser;
		if (!headers.containsKey(CustomRequestHeaders.HEADER_AUTH_USER)
				&& (currentUser = CurrentRuntimeContext.getCurrentUser()) != null) {
			header(CustomRequestHeaders.HEADER_AUTH_USER, currentUser.toEncodeString());
		}
		
		return this;
	}

	public boolean isFallbackHitCache() {
		return fallbackHitCache;
	}

	public String getFallbackHitCacheKey() {
		if (!fallbackHitCache)
			return null;
		StringBuilder builder = new StringBuilder();
		builder.append(method).append(uri);
		if (queryParams != null && !queryParams.isEmpty()) {
			builder.append(ParameterUtils.mapToQueryParams(queryParams));
		}
		if(body != null) {
			builder.append(body);
		}
		
		if(headers != null) {
			if(headers.containsKey(CustomRequestHeaders.HEADER_SYSTEM_ID)) {
				builder.append(headers.get(CustomRequestHeaders.HEADER_SYSTEM_ID));
			}
			if(headers.containsKey(CustomRequestHeaders.HEADER_TENANT_ID)) {
				builder.append(headers.get(CustomRequestHeaders.HEADER_TENANT_ID));
			}
		}
		
		return "httpFallbackCache:" + DigestUtils.md5(builder.toString());
	}

	public HttpRequestEntity method(HttpMethod method) {
		this.method = method;
		return this;
	}

	public HttpRequestEntity charset(String charset) {
		this.charset = charset;
		return this;
	}

	public String getContentType() {
		if (StringUtils.isBlank(contentType)) {
			return HttpClientProvider.CONTENT_TYPE_JSON_UTF8;
		}
		return contentType;
	}

	public HttpRequestEntity contentType(String contentType) {
		this.contentType = contentType;
		return this;
	}

	public boolean isMultipart() {
		return multipart;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}
	
	public String getHeader(String headerName) {
		return headers == null ? null : headers.get(headerName);
	}
	
	public boolean hasHeader(String headerName) {
		return headers != null && headers.containsKey(headerName);
	}

	public HttpRequestEntity headers(Map<String, String> headers) {
		this.headers = headers;
		return this;
	}

	public HttpRequestEntity header(String name, String value) {
		if(StringUtils.isBlank(value))return this;
		if(headers == null)headers = new HashMap<>();
		if (this.headers == null)
			this.headers = new HashMap<>(3);
		this.headers.put(name, value);
		return this;
	}
	
	public HttpRequestEntity headerIfAbsent(String name, String value) {
		if(!hasHeader(name)) {
			header(name, value);
		}
		return this;
	}

	public Map<String, Object> getQueryParams() {
		return queryParams;
	}

	public HttpRequestEntity queryParams(Map<String, Object> queryParams) {
		this.queryParams = queryParams;
		return this;
	}

	public HttpRequestEntity queryParam(String name, Object value) {
		if(value == null || StringUtils.isBlank(value.toString()))return this;
		if (this.queryParams == null)
			this.queryParams = new HashMap<>(3);
		this.queryParams.put(name, value);
		return this;
	}

	public Map<String, Object> getFormParams() {
		return formParams;
	}
	
	public Proxy getProxy() {
		return proxy;
	}

	public HttpRequestEntity fileParam(String name, File file) {
		if (this.formParams == null)
			this.formParams = new HashMap<>();
		this.formParams.put(name, new FileItem(file));
		if (contentType == null) {
			contentType = HttpClientProvider.CONTENT_TYPE_FROM_MULTIPART_UTF8;
		}
		if (!multipart) {
			multipart = true;
			boundary = String.valueOf(System.nanoTime()); // 随机分隔线
			contentType = contentType + ";boundary=" + boundary;
		}
		return this;
	}

	public HttpRequestEntity fileParam(String name, String originalFilename, InputStream inputStream, String mimeType,
			long size) {
		if (this.formParams == null)
			this.formParams = new HashMap<>();
		this.formParams.put(name, new FileItem(originalFilename, inputStream, mimeType, size));
		if (contentType == null) {
			contentType = HttpClientProvider.CONTENT_TYPE_FROM_MULTIPART_UTF8;
		}
		if (!multipart) {
			multipart = true;
			boundary = String.valueOf(System.nanoTime()); // 随机分隔线
			contentType = contentType + ";boundary=" + boundary;
		}
		return this;
	}

	public HttpRequestEntity fileParam(String name, String originalFilename, byte[] data, String mimeType) {
		if (this.formParams == null)
			this.formParams = new HashMap<>();
		this.formParams.put(name, new FileItem(originalFilename, data, mimeType));
		if (contentType == null) {
			contentType = HttpClientProvider.CONTENT_TYPE_FROM_MULTIPART_UTF8;
		}
		if (!multipart) {
			multipart = true;
			boundary = String.valueOf(System.nanoTime()); // 随机分隔线
			contentType = contentType + ";boundary=" + boundary;
		}
		return this;
	}

	@SuppressWarnings("unchecked")
	public HttpRequestEntity formParams(Object formdata) {
		if (formdata instanceof Map) {
			this.formParams = (Map<String, Object>) formdata;
		} else {
			this.formParams = BeanUtils.beanToMap(formdata);
		}
		return this;
	}

	public HttpRequestEntity formParam(String name, String value) {
		if (this.formParams == null)
			this.formParams = new HashMap<>();
		this.formParams.put(name, value);
		if (contentType == null) {
			contentType = HttpClientProvider.CONTENT_TYPE_FROM_URLENCODED_UTF8;
		}
		return this;
	}

	public String getBody() {
		if(body == null 
				&& HttpMethod.POST.equals(method) 
				&& getContentType().startsWith(HttpClientProvider.CONTENT_TYPE_JSON_PREFIX)) {
			body = "{}";
		}
		return body;
	}

	public HttpRequestEntity body(String body) {
		if (method != HttpMethod.POST || body == null) {
			return null;
		}
		this.body = body.toString();
		return this;
	}
	
	public HttpRequestEntity objectBody(Object body) {
		if (method != HttpMethod.POST || body == null) {
			return null;
		}
		if(body instanceof String) {
			this.body = body.toString();
		}else {
			this.body = JsonUtils.toJson(body);
		}
		return this;
	}

	public String getBoundary() {
		return boundary;
	}

	public BasicAuthParams getBasicAuth() {
		return basicAuth;
	}

	public HttpRequestEntity basicAuth(String userName, String password) {
		this.basicAuth = new BasicAuthParams(userName, password);
		return this;
	}

	public static String parseContentTypeCharset(String contentType) {
		String charset = HttpClientProvider.CHARSET_UTF8;
		if (StringUtils.isBlank(contentType))
			return charset;
		String[] params = StringUtils.split(contentType, ";");
		for (String param : params) {
			param = param.trim();
			if (param.toLowerCase().startsWith("charset")) {
				String[] pair = param.split("=", 2);
				if (pair.length == 2) {
					if (!StringUtils.isEmpty(pair[1])) {
						charset = pair[1].trim();
					}
				}
				break;
			}
		}

		return charset;
	}
	
	public StringBuilder buildRequestLog() {
		//http-nio- ,reactor-http
		//后台异步任务不打印
		if(!GlobalContext.isStarting() && !Thread.currentThread().getName().contains(HTTP_THREAD_NAME)) {
			return null;
		}
		StringBuilder builder = new StringBuilder();
		builder.append("\n---------------backend request trace start--------------------");
		builder.append("\nurl:").append(uri);
		if(headers != null) {
			builder.append("\nheaders");
			for (String header : headers.keySet()) {
				builder.append("\n - ").append(header).append(":").append(headers.get(header));
			}
		}
		if(getQueryParams() != null && !getQueryParams().isEmpty()) {
			builder.append("\nqueryParams:").append(getQueryParams());
		}
		if(getBody() != null) {
			if(body.length() > 500) {
				builder.append("\nbody:").append(body.substring(0, 500) + "...");
			}else {
				builder.append("\nbody:").append(body);
			}
		}
		return builder;
	}

	public HttpResponseEntity execute() {
		return HttpUtils.execute(this);
	}

	public void unset() {
		if (!isMultipart())
			return;
		Object entryValue;
		for (Entry<String, Object> entry : formParams.entrySet()) {
			entryValue = entry.getValue();
			if (entryValue == null)
				continue;
			if (entryValue instanceof FileItem) {
				FileItem fileItem = (FileItem) entryValue;
				if (fileItem.content != null) {
					fileItem.content = null;
				} else if (fileItem.createdTempFile) {
					try {
						FileUtils.forceDelete(fileItem.file);
						fileItem.createdTempFile = false;
					} catch (Exception e) {
					}
				}
			}
		}
	}

	public static class BasicAuthParams {
		private String name;
		private String password;

		public BasicAuthParams(String name, String password) {
			super();
			this.name = name;
			this.password = password;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}

		public String getEncodeBasicAuth() {
			String encoded = Base64.encodeToString((name + ":" + password).getBytes(StandardCharsets.UTF_8), false);
			return "Basic " + encoded;
		}

	}

	/**
	 * 文件元数据。
	 */
	public static class FileItem {
		// 每块限制大小，超过的要分块上传
		private static int chunkSizeLimit = ResourceUtils.getInt("mendmix-cloud.httputil.fileupload.chunkSizeLimit",
				1024 * 1024);
		private static String tmpDir = System.getProperty("java.io.tmpdir") + File.separator + "jeesuite";
		static {
			File dir = new File(tmpDir);
			if (!dir.exists()) {
				dir.mkdirs();
			}
		}
		private String fileName;
		private String mimeType;
		private byte[] content;
		private File file;
		private InputStream inputStream;
		private long size;

		private int chunkNum = 1;

		private long offset;
		private boolean createdTempFile;

		/**
		 * 基于本地文件的构造器。
		 * 
		 * @param file
		 *            本地文件
		 */
		public FileItem(File file) {
			this.file = file;
			setFileName(file.getName());
			setSize(file.length());
		}

		/**
		 * 基于文件绝对路径的构造器。
		 * 
		 * @param filePath
		 *            文件绝对路径
		 */
		public FileItem(String filePath) {
			this(new File(filePath));
		}

		/**
		 * 基于文件名和字节流的构造器。
		 * 
		 * @param fileName
		 *            文件名
		 * @param content
		 *            文件字节流
		 */
		public FileItem(String fileName, byte[] content) {
			this.content = content;
			this.size = content.length;
			setFileName(fileName);
		}

		/**
		 * 基于文件名、字节流和媒体类型的构造器。
		 * 
		 * @param fileName
		 *            文件名
		 * @param content
		 *            文件字节流
		 * @param mimeType
		 *            媒体类型
		 */
		public FileItem(String fileName, byte[] content, String mimeType) {
			this(fileName, content);
			if (mimeType != null) {
				this.mimeType = mimeType;
			}
		}

		public FileItem(String originalFilename, InputStream inputStream, String mimeType, long size) {
			setFileName(originalFilename);
			if (mimeType != null) {
				this.mimeType = mimeType;
			}
			setSize(size);
			// 写入临时文件
			if (chunkNum > 1) {
				String ext = null;
				if (originalFilename.contains(GlobalConstants.DOT)) {
					ext = originalFilename.substring(originalFilename.lastIndexOf(GlobalConstants.DOT));
				} else {
					ext = GlobalConstants.DOT
							+ StringUtils.defaultIfBlank(MimeTypeUtils.getFileExtension(mimeType), "tmp");
				}

				this.file = new File(tmpDir, UUID.randomUUID().toString() + ext);

				FileOutputStream outputStream = null;
				try {
					outputStream = new FileOutputStream(file);
					IOUtils.copy(inputStream, outputStream);
					createdTempFile = true;
				} catch (Exception e) {
					throw new MendmixBaseException("写入临时文件错误:" + e.getMessage());
				} finally {
					try {
						if (outputStream != null)
							outputStream.close();
					} catch (Exception e2) {
					}
				}
			} else {
				this.inputStream = inputStream;
			}
		}

		public String getFileName() {
			return this.fileName;
		}

		public String getMimeType() throws IOException {
			if (this.mimeType == null) {
				FileMeta fileMeta = MimeTypeUtils.getFileMeta(getContent());
				if (fileMeta != null) {
					return fileMeta.getMimeType();
				}
			}
			return this.mimeType;
		}

		public InputStream getInputStream() {
			return inputStream;
		}

		public long getSize() {
			return size;
		}

		public int getChunkNum() {
			return chunkNum;
		}

		public void setFileName(String fileName) {
			this.fileName = fileName;
			if (fileName.contains(GlobalConstants.DOT)) {
				String extension = fileName.substring(fileName.lastIndexOf(GlobalConstants.DOT) + 1).toLowerCase();
				this.mimeType = MimeTypeUtils.getFileMimeType(extension);
			}
		}

		private void setSize(long size) {
			this.size = size;
			if (chunkSizeLimit > 0 && size > chunkSizeLimit) {
				chunkNum = (int) (size / chunkSizeLimit);
				if ((size / chunkSizeLimit) > 0) {
					chunkNum = chunkNum + 1;
				}
			}
		}

		public byte[] getContent() throws IOException {
			if (chunkNum > 1) {
				if (offset >= size)
					return new byte[0];
				byte[] data = getFileChunk(file, offset, chunkSizeLimit);
				offset = offset + chunkSizeLimit;
				if (offset > size)
					offset = size;
				return data;
			}
			if (this.content != null)
				return this.content;
			if (inputStream == null && this.file != null && this.file.exists()) {
				inputStream = new FileInputStream(this.file);
			}
			if (inputStream == null)
				return null;
			ByteArrayOutputStream out = null;

			try {
				out = new ByteArrayOutputStream();
				int ch;
				while ((ch = inputStream.read()) != -1) {
					out.write(ch);
				}
				this.content = out.toByteArray();
			} finally {
				if (out != null) {
					out.close();
				}
				if (inputStream != null) {
					inputStream.close();
					inputStream = null;
				}
			}
			return this.content;
		}

		/**
		 * 文件分块工具
		 * 
		 * @param file
		 *            文件
		 * @param offset
		 *            起始偏移位置
		 * @param chunkSize
		 *            分块大小
		 * @return 分块数据
		 */
		private static byte[] getFileChunk(File file, long offset, int chunkSize) {

			byte[] result = new byte[chunkSize];
			RandomAccessFile accessFile = null;
			try {
				accessFile = new RandomAccessFile(file, "r");
				accessFile.seek(offset);
				int readSize = accessFile.read(result);
				if (readSize == -1) {
					return null;
				} else if (readSize == chunkSize) {
					return result;
				} else {
					byte[] tmpByte = new byte[readSize];
					System.arraycopy(result, 0, tmpByte, 0, readSize);
					return tmpByte;
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (accessFile != null) {
					try {
						accessFile.close();
					} catch (IOException e1) {
					}
				}
			}
			return null;
		}

	}
}
