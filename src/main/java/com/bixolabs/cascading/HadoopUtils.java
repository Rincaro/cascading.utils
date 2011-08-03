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

import java.io.IOException;
import java.util.Properties;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.ClusterStatus;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobTracker.State;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import cascading.flow.FlowConnector;
import cascading.flow.MultiMapReducePlanner;

public class HadoopUtils {
    private static final Logger LOGGER = Logger.getLogger(HadoopUtils.class);
    
	public static final int DEFAULT_STACKSIZE = 512;
	
    private static final long STATUS_CHECK_INTERVAL = 10000;
	
    public static void safeRemove(FileSystem fs, Path path) {
    	if ((fs != null) && (path != null)) {
    		try {
    			fs.delete(path, true);
    		} catch (Throwable t) {
    			// Ignore
    		}
    	}
    }
    
    @SuppressWarnings("deprecation")
    public static JobConf getDefaultJobConf() throws IOException, InterruptedException {
    	return getDefaultJobConf(DEFAULT_STACKSIZE);
    }
    
    /**
     * Return the number of reducers, and thus the max number of parallel reduce tasks.
     * 
     * @param conf
     * @return number of reducers
     * @throws IOException
     * @throws InterruptedException
     */
    @SuppressWarnings("deprecation")
    public static int getNumReducers(JobConf conf) throws IOException, InterruptedException {
        ClusterStatus status = safeGetClusterStatus(conf);
        return status.getMaxReduceTasks();
    }
    
    @SuppressWarnings("deprecation")
    public static int getTaskTrackers(JobConf conf) throws IOException, InterruptedException {
        ClusterStatus status = safeGetClusterStatus(conf);
        return status.getTaskTrackers();
    }
    
    @SuppressWarnings("deprecation")
    public static JobConf getDefaultJobConf(int stackSizeInKB) throws IOException, InterruptedException {
        JobConf conf = new JobConf();
        
        // We explicitly set task counts to 1 for local so that code which depends on
        // things like the reducer count runs properly.
        if (isJobLocal(conf)) {
            conf.setNumMapTasks(1);
            conf.setNumReduceTasks(1);
        } else {

            conf.setNumReduceTasks(getNumReducers(conf));
        }
        
        conf.setMapSpeculativeExecution(false);
        conf.setReduceSpeculativeExecution(false);
        
        conf.set("mapred.child.java.opts", String.format("-server -Xmx512m -Xss%dk", stackSizeInKB));

        // Should match the value used for Xss above. Note no 'k' suffix for the ulimit command.
        // New support that one day will be in Hadoop.
        conf.set("mapred.child.ulimit.stack", String.format("%d", stackSizeInKB));

        return conf;
    }

    public static void setLoggingProperties(Properties props, Level cascadingLevel, Level bixoLevel) {
    	props.put("log4j.logger", String.format("cascading=%s,bixo=%s", cascadingLevel, bixoLevel));
    }
    
    @SuppressWarnings({ "unchecked", "deprecation" })
	public static Properties getDefaultProperties(Class appJarClass, boolean debugging, JobConf conf) {
        Properties properties = new Properties();

        // Use special Cascading hack to control logging levels for code running as Hadoop jobs
        if (debugging) {
            properties.put("log4j.logger", "cascading=DEBUG,bixo=TRACE");
        } else {
            properties.put("log4j.logger", "cascading=INFO,bixo=INFO");
        }

        FlowConnector.setApplicationJarClass(properties, appJarClass);

        // Put the JobConf into the properties, so that when this properties file
        // is used to create the cascading Flow, values in the JobConf are used to
        // set up Hadoop.
        MultiMapReducePlanner.setJobConf(properties, conf);

        return properties;
    }
    
    @SuppressWarnings("deprecation")
    public static boolean isJobLocal(JobConf conf) {
        return conf.get( "mapred.job.tracker" ).equalsIgnoreCase( "local" );
    }
    
    /**
     * Utility routine that tries to ensure the cluster is "stable" (slaves have reported in) so
     * that it's safe to call things like maxReduceTasks.
     * 
     * @param conf
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    @SuppressWarnings("deprecation")
    private static ClusterStatus safeGetClusterStatus(JobConf conf) throws IOException, InterruptedException {
        JobClient jobClient = new JobClient(conf);
        int numTaskTrackers = -1;
        
        while (true) {
            ClusterStatus status = jobClient.getClusterStatus();
            if (status.getJobTrackerState() == State.RUNNING) {
                int curTaskTrackers = status.getTaskTrackers();
                if (curTaskTrackers == numTaskTrackers) {
                    return status;
                } else {
                    // Things are still settling down, so keep looping.
                    if (numTaskTrackers != -1) {
                        LOGGER.trace(String.format("Got incremental update to number of task trackers (%d to %d)", numTaskTrackers, curTaskTrackers));
                    }
                    
                    numTaskTrackers = curTaskTrackers;
                }
            }
            
            if (!isJobLocal(conf)) {
                LOGGER.trace("Sleeping during status check");
                Thread.sleep(STATUS_CHECK_INTERVAL);
            }
        }
    }


}
