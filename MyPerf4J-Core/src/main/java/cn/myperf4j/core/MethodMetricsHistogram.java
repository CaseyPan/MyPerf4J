package cn.myperf4j.core;

import cn.myperf4j.base.MethodTag;
import cn.myperf4j.base.config.ProfilingConfig;
import cn.myperf4j.base.metric.MethodMetrics;
import cn.myperf4j.base.util.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by LinShunkang on 2019/07/23
 */
public class MethodMetricsHistogram {

    private static final ConcurrentHashMap<Integer, MethodMetricsInfo> METHOD_MAP = new ConcurrentHashMap<>(1024 * 8);

    public static void recordMetrics(MethodMetrics metrics) {
        recordMetrics0(metrics.getMethodTagId(), metrics.getTP95(), metrics.getTP99(), metrics.getTP999(), metrics.getTP9999());
    }

    public static void recordNoneMetrics(int methodTagId) {
        recordMetrics0(methodTagId, -1, -1, -1, -1);
    }

    private static void recordMetrics0(int methodTagId, int tp95, int tp99, int tp999, int tp9999) {
        MethodMetricsInfo methodMetricsInfo = METHOD_MAP.get(methodTagId);
        if (methodMetricsInfo != null) {
            methodMetricsInfo.add(tp95, tp99, tp999, tp9999);
            return;
        }

        MethodMetricsInfo info = new MethodMetricsInfo(tp95, tp99, tp999, tp9999);
        METHOD_MAP.put(methodTagId, info);
    }

    public static void buildSysGenProfilingFile() {
        long startMills = System.currentTimeMillis();
        String filePath = ProfilingConfig.getInstance().getSysProfilingParamsFile();
        String tempFilePath = filePath + "_tmp";
        File tempFile = new File(tempFilePath);
        try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(tempFile, false), 8 * 1024)) {
            fileWriter.write("#This is a file automatically generated by MyPerf4J, please do not edit!\n");

            List<Integer> neverInvokedMethods = new ArrayList<>(128);
            MethodTagMaintainer tagMaintainer = MethodTagMaintainer.getInstance();
            for (Map.Entry<Integer, MethodMetricsInfo> entry : METHOD_MAP.entrySet()) {
                Integer methodId = entry.getKey();
                MethodMetricsInfo info = entry.getValue();
                if (info.getCount() <= 0) {
                    neverInvokedMethods.add(methodId);
                    continue;
                }

                int mostTimeThreshold = calMostTimeThreshold(info);
                writeProfilingInfo(tagMaintainer, fileWriter, methodId, mostTimeThreshold);
            }
            fileWriter.flush();

            if (!neverInvokedMethods.isEmpty()) {
                fileWriter.write("#The following methods have never been invoked!\n");
                for (int i = 0; i < neverInvokedMethods.size(); i++) {
                    Integer methodId = neverInvokedMethods.get(i);
                    writeProfilingInfo(tagMaintainer, fileWriter, methodId, 128);
                }
                fileWriter.flush();
            }

            File destFile = new File(filePath);
            boolean rename = tempFile.renameTo(destFile) && destFile.setReadOnly();
            Logger.debug("MethodMetricsHistogram.buildSysGenProfilingFile(): rename " + tempFile.getName() + " to " + destFile.getName() + " " + (rename ? "success" : "fail"));
        } catch (Exception e) {
            Logger.error("MethodMetricsHistogram.buildSysGenProfilingFile()", e);
        } finally {
            Logger.debug("MethodMetricsHistogram.buildSysGenProfilingFile() finished, cost=" + (System.currentTimeMillis() - startMills) + "ms");
        }
    }

    private static void writeProfilingInfo(MethodTagMaintainer tagMaintainer,
                                           BufferedWriter fileWriter,
                                           Integer methodId,
                                           int mostTimeThreshold) throws IOException {
        MethodTag methodTag = tagMaintainer.getMethodTag(methodId);
        fileWriter.write(methodTag.getFullDesc());
        fileWriter.write('=');

        fileWriter.write(mostTimeThreshold + ":" + calOutThresholdCount(mostTimeThreshold));
        fileWriter.newLine();
    }

    private static int calMostTimeThreshold(MethodMetricsInfo info) {
        int count = info.getCount();
        long tp9999Avg = info.getTp9999Sum() / count;
        if (tp9999Avg <= 64) {
            return 64;
        } else if (tp9999Avg <= 128) {
            return 128;
        } else if (tp9999Avg <= 256) {
            return 256;
        }

        long tp999Avg = info.getTp999Sum() / count;
        if (tp999Avg <= 128) {
            return 128;
        } else if (tp999Avg <= 256) {
            return 256;
        } else if (tp999Avg <= 512) {
            return 512;
        }

        long tp99Avg = info.getTp99Sum() / count;
        if (tp99Avg <= 256) {
            return 256;
        } else if (tp99Avg <= 512) {
            return 512;
        } else if (tp99Avg <= 1024) {
            return 1024;
        }

        long tp95Avg = info.getTp95Sum() / count;
        if (tp95Avg <= 512) {
            return 512;
        } else if (tp95Avg <= 1024) {
            return 1024;
        } else if (tp95Avg <= 1536) {
            return 1536;
        }
        return 2048;
    }

    private static int calOutThresholdCount(int mostTimeThreshold) {
        if (mostTimeThreshold <= 256) {
            return 8;
        } else if (mostTimeThreshold <= 512) {
            return 16;
        } else if (mostTimeThreshold <= 1024) {
            return 32;
        } else if (mostTimeThreshold <= 1536) {
            return 64;
        } else {
            return 128;
        }
    }

}
