/**
 * Copyright 2010 TransPac Software, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bixolabs.cascading;


/**
 * Type that can be used as the grouping field value in a Tuple, where
 * the range of values and hash codes should be exactly equal to the
 * number of reducers, to ensure one group and one group only gets passed
 * to each of the reducers.
 * 
 * It's up to the Flow constructor to ensure that there are exactly the
 * same number of reduce tasks as there are reducers. If there are more
 * reducer tasks than reducers, you can still wind up with multiple
 * lists getting passed to the same reducer, as two different tasks.
 *
 */
public class PartitioningKey {
    private int _value;
    private String _ref;
    
    public PartitioningKey() {
        _value = 0;
        _ref = null;
    }
    
    public PartitioningKey(String ref, int numReducerTasks) {
        _ref = ref;
        _value = (ref.hashCode() & Integer.MAX_VALUE) % numReducerTasks;
    }
    
    public String getRef() {
        return _ref;
    }
    
    public int getValue() {
        return _value;
    }
   
}
