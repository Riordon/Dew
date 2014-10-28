package com.intel.sto.bigdata.dew.service.sysmetrics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.intel.sto.bigdata.dew.exception.ServiceException;
import com.intel.sto.bigdata.dew.utils.Files;

public class DstatProcessor extends Thread {
  private Process process;
  private File basePath = Files.getDstatDataPath();

  @Override
  public void run() {

    try {
      File tmpFile = new File(basePath, String.valueOf(System.currentTimeMillis()));
      if (tmpFile.exists()) {
        tmpFile.delete();
      }
      tmpFile.createNewFile();
      String[] cmd =
          {
              "/bin/sh",
              "-c",
              "dstat --mem --io --cpu --net -N eth0,eth1,total --disk --output "
                  + tmpFile.getAbsolutePath()};
      process = Runtime.getRuntime().exec(cmd);
      BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line;
      while((line = br.readLine())!=null){
        // do nothing.
        // only output 671 line data if not read process.inputStream. 
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void startThread() {
    this.setDaemon(true);
    this.start();
  }

  public String findWorkloadMetrics(long startTime, long endTime) throws ServiceException {
    long firstTime = findQueryFile(startTime);

    if (firstTime == -1) {
      throw new ServiceException("No dstat data!");
    }

    if (firstTime == -2) {
      throw new ServiceException("Service start time is later than your query time.");
    }

    StringBuilder sb = new StringBuilder();
    try {
      FileReader fr = new FileReader(new File(basePath, String.valueOf(firstTime)));
      BufferedReader br = new BufferedReader(fr);
      String line;
      int headStep = 0;
      int timeStep = 0;
      long timeSkip = (startTime - firstTime) / 1000;
      int dataSize = 0;
      long duration = (endTime - startTime) / 1000;
      while ((line = br.readLine()) != null) {
        if (headStep++ < 7) { // skip 7 line head data
          continue;
        }
        if (timeStep++ < timeSkip) {
          continue;
        }
        sb.append(line + ";");
        if (dataSize++ == duration) {
          break;
        }
      }
      fr.close();

      if (dataSize < duration) {
        throw new ServiceException("No enough dstat data!");
      }
    } catch (Exception e) {
      throw new ServiceException(e.getMessage());
    }
    return sb.toString();
  }

  public void kill() {
    if (process != null) {
      process.destroy();
      process = null;
    }
  }

  private long findQueryFile(long startTime) {
    List<Long> files = getDstatFileArray();
    if (files.isEmpty()) {
      return -1; // no dstat data.
    }
    if ((new File(basePath, String.valueOf(startTime))).exists()) {
      return startTime;
    }
    files.add(startTime);
    Collections.sort(files);
    int j = 0;
    for (int i = 0; i < files.size(); i++) {
      j = i;
      if (files.get(i) == startTime) {
        break;
      }
    }
    if (j == 0) {
      return -2;// query start time more early than dstat time.
    } else {
      return files.get(j - 1);
    }
  }

  private List<Long> getDstatFileArray() {
    String[] files = basePath.list();
    List<Long> result = new ArrayList<Long>();
    if (files != null && files.length > 0) {
      for (String file : files) {
        try {
          result.add(Long.valueOf(file));
        } catch (Exception e) {
          // do nothing.
        }
      }
    }

    return result;
  }

  /**
   * @param args
   * @throws InterruptedException
   */
  public static void main(String[] args) throws InterruptedException {

    DstatProcessor dp = new DstatProcessor();
    dp.startThread();
    Thread.sleep(5000);
    dp.kill();
  }

}