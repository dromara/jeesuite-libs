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
package org.dromara.mendmix.amqp.adapter.kafka;

public class OffsetAndMetadataStat {

	
	private long partition;
	private long offset;
	private boolean commited = true;

	
	
	public OffsetAndMetadataStat() {}

	public OffsetAndMetadataStat(long partition, long offset) {
		this.partition = partition;
		this.offset = offset;
	}

	public long getPartition() {
		return partition;
	}

	public void setPartition(long partition) {
		this.partition = partition;
	}

	public long getOffset() {
		return offset;
	}

	public void setOffset(long offset) {
		this.offset = offset;
	}

	public boolean isCommited() {
		return commited;
	}

	public void setCommited(boolean commited) {
		this.commited = commited;
	}
	
	
	
	public void updateOnConsumed(long offset) {
		this.offset = offset;
		this.commited = false;
	}

}
