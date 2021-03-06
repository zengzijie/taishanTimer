package com.skyinfo.taishantimer.test.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.skyinfo.taishantimer.test.dao.INucleicDao;
import com.skyinfo.taishantimer.test.dao.ITimestampDao;
import com.skyinfo.taishantimer.test.dao.IUserEnterDao;
import com.skyinfo.taishantimer.test.entity.DataTimestamp;
import com.skyinfo.taishantimer.test.entity.Nucleic;
import com.skyinfo.taishantimer.test.entity.UserEnter;
import com.skyinfo.taishantimer.test.util.EncryptUtils;
import com.skyinfo.taishantimer.test.util.Httpsget;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author ZZI
 */
@Component
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NucleicTimer {

    private static final Logger logger = LoggerFactory.getLogger(NucleicTimer.class);

    @Autowired
    INucleicDao iNucleicDao;
    @Autowired
    IUserEnterDao iUserEnterDao;

    @Autowired
    ITimestampDao iTimestampDao;

    @Value("${nucleic.http.url:}")
    private String url;

    @Value("${nucleic.http.enterUrl:}")
    private String enterUrl;

    @Scheduled(cron = "0 0 * * * ?")
    @PostConstruct
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void nucleicTimerJop() {
        String rksj = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        long timestamp = System.currentTimeMillis();
        if (StringUtils.isEmpty(url)) {
            return;
        }
        logger.info("????????????????????????????????????");
        int pageIndex = 1;
        List<Nucleic> dataList = new ArrayList<>();
        JSONObject params = new JSONObject();
        //??????????????????????????????2021.1.1????????????
        if (iNucleicDao.count() > 0) {
            params.put("updateTimestampStart", iTimestampDao.getMaxTimestamp("1"));
        } else {
            params.put("updateTimestampStart", "1609430400000");
        }
        while (true) {
            params.put("pageIndex", pageIndex);
            params.put("pageSize", 100);
            pageIndex += 1;
            String result = Httpsget.doGet(url, params);
            //????????????????????????
            if (StringUtils.isNotEmpty(result) && JSONObject.parseObject(result).getBoolean("success")) {
                if (pageIndex==2){
                    DataTimestamp dataTimestamp = new DataTimestamp();
                    dataTimestamp.setTimestamp(timestamp);
                    dataTimestamp.setRksj(rksj);
                    dataTimestamp.setType("1");
                    iTimestampDao.save(dataTimestamp);
                }
                //???????????????????????????
                List<JSONObject> resultList = JSONObject.parseObject(result).getJSONObject("data").getJSONArray("data").toJavaList(JSONObject.class);
                logger.info("????????????????????????" + resultList.size());
                if (resultList.size() > 0) {
                    //????????????????????????
                    resultList.forEach(p -> {
                        //???????????????????????????
                        for (String key : p.keySet()) {
                            p.put(key, EncryptUtils.decrypt(p.getString(key), "jnh2021"));
                        }
                        //??????????????????
                        p.put("rksj", rksj);
                        dataList.add(p.toJavaObject(Nucleic.class));
                    });
                } else {
                    //???????????????????????????????????????????????????????????????????????????????????????
                    if (dataList.size() > 0) {
                        savaNucleicList(dataList);
                    }
                    logger.info("????????????????????????????????????");
                    break;
                }
                //???1000???????????????
                if (dataList.size() >= 1000) {
                    savaNucleicList(dataList);
                    dataList.clear();
                }
            } else {
                logger.info("??????????????????????????????");
                break;
            }
        }
        logger.info("????????????????????????????????????");
    }

    @Scheduled(cron = "0 */5 * * * ?")
    @PostConstruct
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void enterTimerJop() {
        String rksj = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        long timestamp = System.currentTimeMillis();
        if (StringUtils.isEmpty(enterUrl)) {
            return;
        }
        logger.info("????????????????????????????????????");
        int pageIndex = 1;
        List<UserEnter> dataList = new ArrayList<>();
        JSONObject params = new JSONObject();
        if (iUserEnterDao.count() > 0) {
            params.put("createTimestampStart", iTimestampDao.getMaxTimestamp("2"));
        } else {
            params.put("createTimestampStart", "1609430400000");
        }
        while (true) {
            params.put("pageIndex", pageIndex);
            params.put("pageSize", 100);
            pageIndex += 1;
            String result = Httpsget.doGet(enterUrl, params);
            if (StringUtils.isNotEmpty(result) && JSONObject.parseObject(result).getBoolean("success")) {
                if (pageIndex==2){
                    DataTimestamp dataTimestamp = new DataTimestamp();
                    dataTimestamp.setTimestamp(timestamp);
                    dataTimestamp.setRksj(rksj);
                    dataTimestamp.setType("2");
                    iTimestampDao.save(dataTimestamp);
                }
                List<JSONObject> resultList = JSONObject.parseObject(result).getJSONObject("data").getJSONArray("data").toJavaList(JSONObject.class);
                logger.info("??????????????????" + resultList.size());
                if (resultList.size() > 0) {
                    resultList.forEach(p -> {
                        for (String key : p.keySet()) {
                            p.put(key, EncryptUtils.decrypt(p.getString(key), "jnh2021"));
                        }
                        p.put("user_id", p.getString("id"));
                        p.put("rksj", rksj);
                        dataList.add(p.toJavaObject(UserEnter.class));
                    });
                } else {
                    if (dataList.size() > 0) {
                        savaUserEnterList(dataList);
                    }
                    logger.info("??????????????????????????????");
                    break;
                }
                if (dataList.size() >= 1000) {
                    savaUserEnterList(dataList);
                    dataList.clear();
                }
            } else {
                logger.info("????????????????????????");
                break;
            }
        }
        logger.info("????????????????????????????????????");
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void savaNucleicList(List<Nucleic> dataList) {
        logger.info("??????????????????" + dataList.size());
        iNucleicDao.saveAll(dataList);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void savaUserEnterList(List<UserEnter> dataList) {
        logger.info("??????????????????" + dataList.size());
        iUserEnterDao.saveAll(dataList);
    }
}
