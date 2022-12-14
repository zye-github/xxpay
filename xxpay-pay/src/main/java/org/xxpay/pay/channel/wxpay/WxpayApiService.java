package org.xxpay.pay.channel.wxpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.binarywang.wxpay.bean.request.BaseWxPayRequest;
import com.github.binarywang.wxpay.bean.request.WxPayDefaultRequest;
import com.github.binarywang.wxpay.bean.request.WxPayFaceAuthInfoRequest;
import com.github.binarywang.wxpay.bean.request.WxPayOrderReverseRequest;
import com.github.binarywang.wxpay.bean.result.*;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.constant.WxPayConstants;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import com.google.gson.JsonObject;
import com.wechat.pay.contrib.apache.httpclient.Validator;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.auth.CertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xxpay.core.common.Exception.ServiceException;
import org.xxpay.core.common.constant.MchConstant;
import org.xxpay.core.common.constant.RetEnum;
import org.xxpay.core.common.util.HttpClient;
import org.xxpay.core.common.util.MyLog;
import org.xxpay.core.common.util.MySeq;
import org.xxpay.core.entity.*;
import org.xxpay.pay.channel.PayConfig;
import org.xxpay.pay.channel.wxpay.request.*;
import org.xxpay.pay.channel.wxpay.utils.RSAEncryptUtil;
import org.xxpay.pay.channel.wxpay.utils.WxHttpsUtil;
import org.xxpay.pay.service.RpcCommonService;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author: dingzhiwei
 * @date: 19/09/05
 * @description: ????????????????????????
 */
@Service
public class WxpayApiService {


    @Autowired
    private PayConfig payConfig;

    @Autowired
    private RpcCommonService rpcCommonService;

    private static final MyLog _log = MyLog.getLog(WxpayApiService.class);

    /**
     * ??????????????????????????????
     * ?????????SDK??????????????????
     * @param mainPayParam
     * @param subPayParam
     * @param rawdata
     * @return
     */
    public JSONObject getWxpayFaceAuthInfo(String mainPayParam, String subPayParam, String storeId, String storeName, String deviceId, String rawdata) {
        String logPrefix = "????????????????????????????????????";
        JSONObject resultJson = new JSONObject();
        // ?????????????????????
        JSONObject mainParam = toDefaultJSONObject(mainPayParam);
        _log.debug("{}mainParam={}", logPrefix, mainParam);
        WxPayConfig wxPayConfig = new WxPayConfig();
        wxPayConfig.setMchId(mainParam.getString("mchId"));
        wxPayConfig.setAppId(mainParam.getString("appId"));
        wxPayConfig.setMchKey(mainParam.getString("key"));
        wxPayConfig.setSignType(WxPayConstants.SignType.MD5);
        // ?????????????????????
        JSONObject subMchParam = toJSONObject(subPayParam);
        _log.debug("{}subMchParam={}", logPrefix, subMchParam);
        WxPayService wxPayService = new WxPayServiceImpl();
        wxPayService.setConfig(wxPayConfig); // ??????????????????

        WxPayFaceAuthInfoRequest request = WxPayFaceAuthInfoRequest.newBuilder().build();

        if(subMchParam != null){ // ???????????????
            request.setSubMchId(subMchParam.getString("subMchId"));
        }

        request.setStoreId(storeId);
        request.setStoreName(storeName);
        request.setDeviceId(deviceId);
        request.setAttach("");
        request.setRawdata(rawdata);
        request.setNow(System.currentTimeMillis()/1000+"");
        request.setVersion("1");

        try {
            WxPayFaceAuthInfoResult result = wxPayService.getWxPayFaceAuthInfo(request); //??????????????????
            resultJson.put("return_code", result.getReturnCode());
            resultJson.put("return_msg", result.getReturnMsg());
            resultJson.put("authinfo", result.getAuthinfo());       // SDK????????????
            resultJson.put("expires_in", result.getExpiresIn());    // authinfo???????????????, ???????????? ??????: 3600 ??????????????????, ?????????????????????????????????????????????????????????(??????????????????????????????????????? ???????????????????????????????????????authinfo???????????????SDK???getWxpayfaceCode?????????
            return resultJson;
        } catch (WxPayException e) {
            _log.error("{}?????????, return_code:{}, return_msg:{}", logPrefix, e.getReturnCode(), e.getReturnMsg());
        }

        return null;

    }

    /** ??????????????????  **/
    public boolean reverse(String mainPayParam, String subPayParam, String payOrderId) {
        String logPrefix = "????????????????????????";
        _log.info("{}, payOrderId={}", logPrefix, payOrderId);
        // ?????????????????????
        JSONObject mainParam = toDefaultJSONObject(mainPayParam);
        _log.debug("{}mainParam={}", logPrefix, mainParam);
        WxPayConfig wxPayConfig = new WxPayConfig();
        wxPayConfig.setMchId(mainParam.getString("mchId"));
        wxPayConfig.setAppId(mainParam.getString("appId"));
        wxPayConfig.setMchKey(mainParam.getString("key"));
        wxPayConfig.setSignType(WxPayConstants.SignType.MD5);
        wxPayConfig.setKeyPath(payConfig.getUploadIsvCertRootDir() + File.separator + mainParam.getString("cert"));

        // ?????????????????????
        JSONObject subMchParam = toJSONObject(subPayParam);
        _log.debug("{}subMchParam={}", logPrefix, subMchParam);
        WxPayService wxPayService = new WxPayServiceImpl();
        wxPayService.setConfig(wxPayConfig); // ??????????????????

        WxPayOrderReverseRequest request = WxPayOrderReverseRequest.newBuilder().build();

        if(subMchParam != null){ // ???????????????
            request.setSubMchId(subMchParam.getString("subMchId"));
        }
        request.setOutTradeNo(payOrderId);

        try {
            WxPayOrderReverseResult result = wxPayService.reverseOrder(request); //??????????????????
            _log.info("{}????????????:", result);
            return true;
        } catch (WxPayException e) {
            _log.error("{}?????????, return_code:{}, return_msg:{}", logPrefix, e.getReturnCode(), e.getReturnMsg());
            return false;
        }
    }



    /** ?????? - ????????????
     * ?????????????????? ???????????????[????????????]????????? ????????????????????????????????????
     * ?????????????????????  ???  ??????????????????
     * */
    public void wxDepositConsume(String mainPayParam, String subPayParam, PayOrder payOrder, Long consumeAmount) {

        String logPrefix = "????????????????????????";

        try {
            // ?????????????????????
            JSONObject mainParam = toDefaultJSONObject(mainPayParam);
            _log.debug("{}mainParam={}", logPrefix, mainParam);
            WxPayConfig wxPayConfig = new WxPayConfig();
            wxPayConfig.setMchId(mainParam.getString("mchId"));
            wxPayConfig.setAppId(mainParam.getString("appId"));
            wxPayConfig.setMchKey(mainParam.getString("key"));
            wxPayConfig.setSignType(WxPayConstants.SignType.MD5);
            // ?????????????????????
            JSONObject subMchParam = toJSONObject(subPayParam);
            _log.debug("{}subMchParam={}", logPrefix, subMchParam);
            WxPayService wxPayService = new WxPayServiceImpl();
            wxPayService.setConfig(wxPayConfig); // ??????????????????


            WxDepositConsumeRequest request = new WxDepositConsumeRequest();
            if(subMchParam != null){ // ???????????????
                request.setSubMchId(subMchParam.getString("subMchId"));
            }

            request.setTotalFee(payOrder.getDepositAmount().intValue()); //??????
            request.setConsumeFee(consumeAmount.intValue());  //????????????
            request.setOutTradeNo(payOrder.getPayOrderId()); //???????????????
            request.setFeeType("CNY");

            //??????
            request.checkAndSign(wxPayService.getConfig());
            String reqUrl = wxPayService.getPayBaseUrl() + "/deposit/consume";
            String responseContent = wxPayService.post(reqUrl, request.toXML(), false);
            WxDepositConsumeResult result = BaseWxPayResult.fromXML(responseContent, WxDepositConsumeResult.class);
            result.checkResult(wxPayService, request.getSignType(), true);

        } catch (WxPayException e) {
            _log.error("{}wxError:{}", logPrefix, e);
            throw ServiceException.build(RetEnum.RET_COMM_OPERATION_FAIL);
        }catch (Exception e) {
            _log.error("{}error:{}", logPrefix, e);
            throw ServiceException.build(RetEnum.RET_COMM_UNKNOWN_ERROR);
        }
    }

    /** ????????????
     *  ???????????????????????????????????? ?????????????????? ???????????????
     * */
    public void wxDepositReverse(String mainPayParam, String subPayParam, String payOrderId) {

        String logPrefix = "??????????????????????????????";
        try {
            // ?????????????????????
            JSONObject mainParam = toDefaultJSONObject(mainPayParam);
            _log.debug("{}mainParam={}", logPrefix, mainParam);
            WxPayConfig wxPayConfig = new WxPayConfig();
            wxPayConfig.setMchId(mainParam.getString("mchId"));
            wxPayConfig.setAppId(mainParam.getString("appId"));
            wxPayConfig.setMchKey(mainParam.getString("key"));
            wxPayConfig.setSignType(WxPayConstants.SignType.MD5);
            // ?????????????????????
            JSONObject subMchParam = toJSONObject(subPayParam);
            _log.debug("{}subMchParam={}", logPrefix, subMchParam);
            WxPayService wxPayService = new WxPayServiceImpl();
            wxPayService.setConfig(wxPayConfig); // ??????????????????


            WxDepositConsumeRequest request = new WxDepositConsumeRequest();

            if(subMchParam != null){ // ???????????????
                request.setSubMchId(subMchParam.getString("subMchId"));
            }

            request.setOutTradeNo(payOrderId); //???????????????

            //??????
            request.checkAndSign(wxPayService.getConfig());
            String reqUrl = wxPayService.getPayBaseUrl() + "/deposit/reverse";
            String responseContent = wxPayService.post(reqUrl, request.toXML(), false);
            WxDepositConsumeResult result = BaseWxPayResult.fromXML(responseContent, WxDepositConsumeResult.class);
            result.checkResult(wxPayService, request.getSignType(), true);

        } catch (WxPayException e) {
            _log.error("{}wxError:{}", logPrefix, e);
            throw ServiceException.build(RetEnum.RET_COMM_OPERATION_FAIL);
        }catch (Exception e) {
            _log.error("{}error:{}", logPrefix, e);
            throw ServiceException.build(RetEnum.RET_COMM_UNKNOWN_ERROR);
        }
    }


    /** ?????? - ??????????????????
     * return JSON { isSuccess: false, errMsg: '', applymentId: '123'}
     * **/
    public JSONObject wxApplymentSubmit(JSONObject isvParam, WxMchSnapshot wxMchSnapshot) {

        String logPrefix = "????????????????????????";
        JSONObject result = new JSONObject();

        try {

            String isvMchId = isvParam.getString("mchId");
            String isvMchKey = isvParam.getString("key");
            String mchV3Key = isvParam.getString("apiV3Key");
            String serialNo = isvParam.getString("serialNo");

            String isvCertPath = payConfig.getUploadIsvCertRootDir() + File.separator + isvParam.getString("cert");
            _log.debug("{}mainParam={}", logPrefix, isvParam);

            String apiClientKeyPath = payConfig.getUploadIsvCertRootDir() + File.separator + isvParam.getString("apiClientKey");
            _log.debug("{}mainParam={}", logPrefix, isvParam);

            PrivateKey mchPrivateKey = PemUtil.loadPrivateKey(new FileInputStream(apiClientKeyPath));
            _log.debug("{}mchPrivateKey={}", logPrefix, mchPrivateKey);

            //????????????????????????
            JSONObject certJson = getCertificate(isvMchId, apiClientKeyPath, serialNo, mchV3Key);
            if(!certJson.getBoolean("isSuccess")){
                throw new IOException("??????????????????????????????");
            }

            String wechatPaySerialNo = certJson.getString("wechatPaySerialNo");
            List<X509Certificate> certificates = (List<X509Certificate>) certJson.get("certs");
            X509Certificate wxCert = certificates.get(0);

            //????????????
            JSONObject request = new JSONObject();
            request.put("business_code", isvMchId + "_" +wxMchSnapshot.getApplyNo()); //??????????????????, ?????????????????????????????????????????????????????????????????????

            //?????????????????????
            JSONObject contactInfo = new JSONObject();
            contactInfo.put("contact_name", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getContact(), wxCert));//?????????????????????
            contactInfo.put("contact_id_number", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getContactIdCardNo(), wxCert));//???????????????????????????
            contactInfo.put("mobile_phone", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getContactPhone(), wxCert));//????????????
            //???????????????
            if(StringUtils.isNotEmpty(wxMchSnapshot.getContactEmail())){
                contactInfo.put("contact_email", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getContactEmail(), wxCert));
            }
            request.put("contact_info", contactInfo);

            //????????????
            JSONObject subjectInfo = new JSONObject();
            subjectInfo.put("subject_type", wxMchSnapshot.getOrganizationType());//????????????

                //????????????
                JSONObject businessLicenseInfo = new JSONObject();
                businessLicenseInfo.put("license_copy", WxHttpsUtil.uploadFile(isvMchId, isvCertPath, isvMchKey, wxMchSnapshot.getBusinessLicenseCopy()));//??????????????????
                businessLicenseInfo.put("license_number", wxMchSnapshot.getBusinessLicenseNumber());//?????????/????????????????????????
                businessLicenseInfo.put("merchant_name", wxMchSnapshot.getBusinessMerchantName());//????????????
                businessLicenseInfo.put("legal_person", wxMchSnapshot.getIdCardName());//??????????????????/????????????

                subjectInfo.put("business_license_info", businessLicenseInfo);

                //?????????/??????????????????
                JSONObject identityInfo = new JSONObject();
                identityInfo.put("id_doc_type", "IDENTIFICATION_TYPE_IDCARD");//???????????????????????????????????????
                identityInfo.put("owner", true);//?????????/????????????????????????

                    //???????????????
                    JSONObject idCardInfo = new JSONObject();
                    idCardInfo.put("id_card_copy", WxHttpsUtil.uploadFile(isvMchId, isvCertPath, isvMchKey, wxMchSnapshot.getIdCardCopy()));//????????????????????????
                    idCardInfo.put("id_card_national", WxHttpsUtil.uploadFile(isvMchId, isvCertPath, isvMchKey, wxMchSnapshot.getIdCardNational()));//????????????????????????
                    idCardInfo.put("id_card_name", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getIdCardName(), wxCert));//???????????????
                    idCardInfo.put("id_card_number", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getIdCardNumber(), wxCert));//???????????????
                    idCardInfo.put("card_period_begin", wxMchSnapshot.getIdCardValidStartTime());//??????????????????????????????
                    idCardInfo.put("card_period_end", wxMchSnapshot.getIdCardValidEndTime());//??????????????????????????????

                    identityInfo.put("id_card_info", idCardInfo);
                subjectInfo.put("identity_info", identityInfo);
            request.put("subject_info", subjectInfo);

            //????????????
            JSONObject businessInfo = new JSONObject();
            businessInfo.put("merchant_shortname", wxMchSnapshot.getMerchantShortName());//????????????
            businessInfo.put("service_phone", wxMchSnapshot.getServicePhone());//????????????

                //????????????
                JSONObject salesInfo = new JSONObject();

                JSONArray salesScenesType = new JSONArray();
                salesScenesType.add(wxMchSnapshot.getSalesSceneType());
                salesInfo.put("sales_scenes_type", salesScenesType);//??????????????????

                    //??????????????????
                    JSONObject bizStoreInfo = new JSONObject();
                    bizStoreInfo.put("biz_store_name", wxMchSnapshot.getStoreName());//????????????
                    bizStoreInfo.put("biz_address_code", wxMchSnapshot.getStoreAddressCode());//??????????????????
                    bizStoreInfo.put("biz_store_address", wxMchSnapshot.getStoreStreet());//????????????

                    JSONArray storeEntrancePic = new JSONArray();
                    storeEntrancePic.add(WxHttpsUtil.uploadFile(isvMchId, isvCertPath, isvMchKey, wxMchSnapshot.getStoreEntrancePic()));
                    bizStoreInfo.put("store_entrance_pic", storeEntrancePic);//??????????????????

                    JSONArray indoorPic = new JSONArray();
                    indoorPic.add(WxHttpsUtil.uploadFile(isvMchId, isvCertPath, isvMchKey, wxMchSnapshot.getIndoorPic()));
                    bizStoreInfo.put("indoor_pic", indoorPic);//??????????????????

                    bizStoreInfo.put("biz_sub_appid", wxMchSnapshot.getMpAppid());//???????????????????????????APPID

                    salesInfo.put("biz_store_info", bizStoreInfo);
                businessInfo.put("sales_info", salesInfo);
            request.put("business_info", businessInfo);

            //????????????
            JSONObject settlementInfo = new JSONObject();
            settlementInfo.put("settlement_id", wxMchSnapshot.getSettlementId());//??????????????????ID
            settlementInfo.put("qualification_type", wxMchSnapshot.getQualificationType());//????????????

            //????????????
            if(StringUtils.isNotEmpty(wxMchSnapshot.getQualifications())){
                String[] qualificationsList = wxMchSnapshot.getQualifications().split(",");

                JSONArray qualifications = new JSONArray();
                for (String str : qualificationsList) {
                    qualifications.add(WxHttpsUtil.uploadFile(isvMchId, isvCertPath, isvMchKey, str));
                }
                settlementInfo.put("qualifications", qualifications);
            }

            request.put("settlement_info", settlementInfo);

            //??????????????????
            JSONObject bankAccountInfo = new JSONObject();
            bankAccountInfo.put("bank_account_type", wxMchSnapshot.getBankAccountType());//????????????
            bankAccountInfo.put("account_name", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getAccountName(), wxCert));//????????????
            bankAccountInfo.put("account_bank", wxMchSnapshot.getAccountBank());//????????????
            bankAccountInfo.put("bank_address_code", wxMchSnapshot.getBankAddressCode());//????????????????????????
            bankAccountInfo.put("bank_name", wxMchSnapshot.getBankName());//??????????????????????????????)
            bankAccountInfo.put("account_number", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getAccountNumber(), wxCert));//????????????

            request.put("bank_account_info", bankAccountInfo);

            //????????????
            JSONObject additionInfo = new JSONObject();
            additionInfo.put("business_addition_msg", wxMchSnapshot.getBusinessAdditionDesc());//????????????

            if(StringUtils.isNotEmpty(wxMchSnapshot.getBusinessAdditionPics())){
                JSONArray additionPics = new JSONArray();
                additionPics.add(WxHttpsUtil.uploadFile(isvMchId, isvCertPath,isvMchKey, wxMchSnapshot.getBusinessAdditionPics()));
                additionInfo.put("business_addition_pics", additionPics);//????????????
            }
            request.put("addition_info", additionInfo);


            //????????????
            String reqUrl = "https://api.mch.weixin.qq.com/v3/applyment4sub/applyment/";

            HttpPost httpPost = new HttpPost(reqUrl);
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.addHeader("Accept", "application/json");
            httpPost.addHeader("Wechatpay-Serial", wechatPaySerialNo);

            StringEntity postingString = new StringEntity(request.toJSONString(), "utf-8");
            httpPost.setEntity(postingString);

            _log.info("=====????????????====" + request.toJSONString());

            WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
                    .withMerchant(isvMchId, serialNo, PemUtil.loadPrivateKey(new FileInputStream(apiClientKeyPath)))
                    .withWechatpay(certificates);

            CloseableHttpClient httpClient = builder.build();

            CloseableHttpResponse response = httpClient.execute(httpPost);

            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity());
            _log.info("=====response_body=====" + body);

            JSONObject res = JSON.parseObject(body);

            if (statusCode == 200) {
                String applymentId = res.getString("applyment_id");

                result.put("isSuccess", true);
                result.put("applymentId", applymentId);
            } else {
                _log.info("apply failed,resp code=" + statusCode);
                throw new IOException("request failed " + "[" + res.getString("message") + "]");
            }
        } /*catch (Exception e) {
            _log.error("{}wxError:{}", logPrefix, e);

            result.put("isSuccess", false);
            result.put("errMsg", e.getReturnMsg() + "|" + e.getErrCodeDes());

        }*/catch (Exception e) {
            _log.error("{}error:{}", logPrefix, e);

            result.put("isSuccess", false);
            result.put("errMsg", "??????????????????????????????????????????????????????, e=" + e.getMessage());
        }

        return result;
    }

    /** ?????? - ????????????????????????
     * return JSON
     * isSuccess : ??????????????????????????????
     * errMsg??? ???????????? / ?????????????????????
     * status: ??????
     * subMchId:
     * signUrl:
     * **/
    public JSONObject wxQueryApplymentSubmit(JSONObject isvParam, WxMchSnapshot wxMchSnapshot) {

        String logPrefix = "????????????????????????????????????";
        JSONObject result = new JSONObject();

        try {
//            if(true){
//                result.put("isSuccess", true);
//                Byte testStatus = MchConstant.WXAPPLY_MICRO_STATUS_OK;
//
//                result.put("applyStatus", testStatus);
//                if(testStatus == MchConstant.WXAPPLY_MICRO_STATUS_ING){  //?????????
//                    result.put("applymentStateDesc", "???????????????");
//
//                }else if(testStatus == MchConstant.WXAPPLY_MICRO_STATUS_FAIL){  //????????????
//                    result.put("errMsg", "????????????????????????");
//
//                }else if(testStatus == MchConstant.WXAPPLY_MICRO_STATUS_WAIT_SIGN){  //?????????
//                    result.put("subMchId", "888888");
//                    result.put("signUrl", "https://xxpayvip.oss-cn-beijing.aliyuncs.com/apply/a6b10f8f-7a6e-4860-98f4-d25ebad5ce5b.png");
//
//                }else if(testStatus == MchConstant.WXAPPLY_MICRO_STATUS_OK){  //????????????
//                    result.put("subMchId", "888888");
//                    result.put("signUrl", "https://xxpayvip.oss-cn-beijing.aliyuncs.com/apply/a6b10f8f-7a6e-4860-98f4-d25ebad5ce5b.png");
//                }
//
//                return result;
//            }

            String isvMchId = isvParam.getString("mchId");
            String mchV3Key = isvParam.getString("apiV3Key");
            String serialNo = isvParam.getString("serialNo");

            String apiClientKeyPath = payConfig.getUploadIsvCertRootDir() + File.separator + isvParam.getString("apiClientKey");
            _log.debug("{}mainParam={}", logPrefix, isvParam);

            //????????????????????????
            JSONObject certJson = getCertificate(isvMchId, apiClientKeyPath, serialNo, mchV3Key);
            if(!certJson.getBoolean("isSuccess")){
                throw new IOException("??????????????????????????????");
            }

            String wechatPaySerialNo = certJson.getString("wechatPaySerialNo");
            List<X509Certificate> certificates = (List<X509Certificate>) certJson.get("certs");
            X509Certificate wxCert = certificates.get(0);

            String bussinessCode = isvMchId + "_" +wxMchSnapshot.getApplyNo(); //??????????????????, ?????????????????????????????????????????????????????????????????????

            //????????????
            String reqUrl = "https://api.mch.weixin.qq.com/v3/applyment4sub/applyment/business_code/" + bussinessCode;

            HttpGet httpGet = new HttpGet(reqUrl);
            httpGet.addHeader("Content-Type", "application/json");
            httpGet.addHeader("Accept", "application/json");
            httpGet.addHeader("Wechatpay-Serial", wechatPaySerialNo);

            WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
                    .withMerchant(isvMchId, serialNo, PemUtil.loadPrivateKey(new FileInputStream(apiClientKeyPath)))
                    .withWechatpay(certificates);

            CloseableHttpClient httpClient = builder.build();

            CloseableHttpResponse response = httpClient.execute(httpGet);

            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity());
            if (statusCode == 200) {
                _log.info("=====body=====" + body);
                JSONObject res = JSON.parseObject(body);
                String applyMentState = res.getString("applyment_state");

                if("APPLYMENT_STATE_EDITTING".equals(applyMentState)){ //?????????
                    result.put("applyStatus", MchConstant.WXAPPLY_MICRO_STATUS_EDITING);
                    result.put("applymentStateDesc", res.getString("applyment_state_msg"));
                }

                if("APPLYMENT_STATE_AUDITING".equals(applyMentState)){ //?????????
                    String singUrl = res.getString("sign_url") == null ? null : res.getString("sign_url");
                    result.put("applyStatus", MchConstant.WXAPPLY_MICRO_STATUS_ING);
                    result.put("signUrl", singUrl);
                    String imgUrl =  payConfig.getPayUrl() + "/qrcode_img_get?url=" + URLEncoder.encode(singUrl);
                    result.put("wxApplymentMchQrImg", imgUrl);
                }

                if("APPLYMENT_STATE_REJECTED".equals(applyMentState)){ //?????????
                    String singUrl = res.getString("sign_url") == null ? null : res.getString("sign_url");
                    result.put("applyStatus", MchConstant.WXAPPLY_MICRO_STATUS_FAIL);
                    result.put("signUrl", singUrl);
                    String imgUrl =  payConfig.getPayUrl() + "/qrcode_img_get?url=" + URLEncoder.encode(singUrl);
                    result.put("wxApplymentMchQrImg", imgUrl);

                    JSONArray auditDetail = JSONArray.parseArray(res.getString("audit_detail"));
                    result.put("errMsg", auditDetail.getJSONObject(0).getString("reject_reason"));
                }

                if("APPLYMENT_STATE_TO_BE_CONFIRMED".equals(applyMentState)){ //???????????????
                    String singUrl = res.getString("sign_url") == null ? null : res.getString("sign_url");
                    result.put("applyStatus", MchConstant.WXAPPLY_MICRO_STATUS_WAIT_VERIFY);
                    result.put("signUrl", singUrl);
                    String imgUrl =  payConfig.getPayUrl() + "/qrcode_img_get?url=" + URLEncoder.encode(singUrl);
                    result.put("wxApplymentMchQrImg", imgUrl);
                }

                if("APPLYMENT_STATE_TO_BE_SIGNED".equals(applyMentState)){ //?????????
                    String singUrl = res.getString("sign_url") == null ? null : res.getString("sign_url");
                    result.put("applyStatus", MchConstant.WXAPPLY_MICRO_STATUS_WAIT_SIGN);
                    result.put("signUrl", singUrl);
                    String imgUrl =  payConfig.getPayUrl() + "/qrcode_img_get?url=" + URLEncoder.encode(singUrl);
                    result.put("wxApplymentMchQrImg", imgUrl);
                }

                if("APPLYMENT_STATE_FINISHED".equals(applyMentState)){ //??????
                    result.put("applyStatus", MchConstant.WXAPPLY_MICRO_STATUS_OK);
                    result.put("subMchId", res.getString("sub_mchid"));
                    result.put("signUrl", res.getString("sign_url") == null ? null : res.getString("sign_url"));
                }

                if("APPLYMENT_STATE_CANCELED".equals(applyMentState)){ //?????????
                    result.put("applyStatus", MchConstant.WXAPPLY_MICRO_STATUS_CANCLE);
                    result.put("errMsg", "?????????");
                }

                result.put("isSuccess", true);
            } else {
                _log.info("download failed,resp code=" + statusCode + ",body=" + body);
                throw new IOException("request failed");
            }
        } catch (Exception e) {
            _log.error("{}error:{}", logPrefix, e);
            result.put("isSuccess", false);
            result.put("errMsg", "??????????????????????????????????????????????????????, e=" + e.getMessage());
        }

        return result;
    }


    /** ?????? - ??????????????????
     * return JSON { isSuccess: false, errMsg: '', applymentId: '123'}
     * **/
    public JSONObject wxApplymentSubmitUpgrade(JSONObject isvParam, WxMchUpgradeSnapshot wxMch) {

        String logPrefix = "????????????????????????";
        JSONObject result = new JSONObject();

        try {

//            if(true){
//                result.put("isSuccess", true);
//                return result;
//            }

            String isvMchId = isvParam.getString("mchId");
            String isvMchKey = isvParam.getString("key");
            String mchV3Key = isvParam.getString("apiV3Key");
            String serialNo = isvParam.getString("serialNo");

            String apiClientKeyPath = payConfig.getUploadIsvCertRootDir() + File.separator + isvParam.getString("apiClientKey");
            _log.debug("{}mainParam={}", logPrefix, isvParam);

            PrivateKey mchPrivateKey = PemUtil.loadPrivateKey(new FileInputStream(apiClientKeyPath));

            String isvCertPath = payConfig.getUploadIsvCertRootDir() + File.separator + isvParam.getString("cert");
            _log.debug("{}mainParam={}", logPrefix, isvParam);
            WxPayConfig wxPayConfig = new WxPayConfig();
            wxPayConfig.setMchId(isvMchId);
            wxPayConfig.setMchKey(isvMchKey);
            wxPayConfig.setKeyPath(isvCertPath);
            wxPayConfig.setSignType(WxPayConstants.SignType.HMAC_SHA256);

            //?????????????????????
            JSONObject certJSON = getCertficatesSerialNo(wxPayConfig.getMchId(), wxPayConfig.getMchKey());
            String certNo = certJSON.getString("serial_no"); //???????????????
            String wxCert = RSAEncryptUtil.getWXCert(certJSON, mchV3Key); //??????????????? ????????????

            WxPayService wxPayService = new WxPayServiceImpl();
            wxPayService.setConfig(wxPayConfig); // ??????????????????

            WxApplymentSubmitUpgradeRequest request = new WxApplymentSubmitUpgradeRequest();
            request.setVersion("1.0"); //?????????????????? 1.0
            request.setCertSn(certNo); //?????????????????????

            request.setSubMchId(wxMch.getSubMchId()); //???????????????
            request.setOrganizationType(wxMch.getOrganizationType()); //???????????? 2-??????   4-??????????????? 3-??????????????????????????????  1708-????????????


            request.setBusinessLicenseCopy(WxHttpsUtil.uploadFile(isvMchId, isvCertPath,isvMchKey, wxMch.getBusinessLicenseCopy()));  //????????????????????? ??????
            request.setBusinessLicenseNumber(wxMch.getBusinessLicenseNumber()); //?????????????????????
            request.setMerchantName(wxMch.getMerchantName());  //????????????
            request.setCompanyAddress(wxMch.getCompanyAddress()); //????????????
            //request.setLegalPerson(RSAEncryptUtil.rsaEncrypt(wxMch.getLegalPerson(), wxCert)); //???????????????/???????????????  ?????????
            request.setBusinessTime(wxMch.getBusinessTime()); //????????????
            request.setBusinessLicenceType(wxMch.getBusinessLicenceType());  //?????????????????? 1762-???????????????    1763-???????????????

            //???????????????????????????
            if(StringUtils.isNotEmpty(wxMch.getOrganizationCopy())){
                request.setOrganizationCopy(WxHttpsUtil.uploadFile(isvMchId, isvCertPath,isvMchKey, wxMch.getOrganizationCopy()));
            }

            request.setOrganizationNumber(wxMch.getOrganizationNumber()); //??????????????????
            request.setOrganizationTime(wxMch.getOrganizationTime()); //??????????????????????????????

            //????????????
            if(StringUtils.isNotEmpty(wxMch.getAccountName())){
                //request.setAccountName(RSAEncryptUtil.rsaEncrypt(wxMch.getAccountName(), wxCert));
            }


            request.setAccountBank(wxMch.getAccountBank());   //????????????
            request.setBankAddressCode(wxMch.getBankAddressCode());   //???????????????????????????
            request.setBankName(wxMch.getBankName()); //?????????????????? ???????????????
            //request.setAccountNumber(RSAEncryptUtil.rsaEncrypt(wxMch.getAccountNumber(), wxCert)); //????????????
            request.setMerchantShortname(wxMch.getMerchantShortname()); //????????????
            request.setBusiness(wxMch.getBusiness()); //??????????????????ID

            //????????????  ???????????????
            request.setQualifications(convertImgArray(wxMch.getQualifications(), isvMchId, isvCertPath, serialNo, isvMchKey));
            request.setBusinessScene(wxMch.getBusinessScene());  //????????????1721-??????  1837-?????????  1838-?????????  1724-APP  1840-PC??????
            request.setBusinessAdditionDesc(wxMch.getBusinessAdditionDesc()); //????????????

            //???????????? ???????????????
            request.setBusinessAdditionPics(convertImgArray(wxMch.getBusinessAdditionPics(), isvMchId, isvCertPath, serialNo, isvMchKey));
            //request.setContactEmail(RSAEncryptUtil.rsaEncrypt(wxMch.getContactEmail(), wxCert));  //????????????????????? ??????

            request.setMpAppid(wxMch.getMpAppid());  //?????????ID
            request.setMpAppScreenShots(convertImgArray(wxMch.getMpAppScreenShots(), isvMchId, isvCertPath, serialNo, isvMchKey)); //?????????????????????

            request.setMiniprogramAppid(wxMch.getMiniprogramAppid());  //?????????APPID
            request.setMiniprogramScreenShots(convertImgArray(wxMch.getMiniprogramAppid(), isvMchId, isvCertPath, serialNo, isvMchKey));//?????????????????????

            request.setAppAppid(wxMch.getAppAppid());  //??????APPID
            request.setAppScreenShots(convertImgArray(wxMch.getAppScreenShots(), isvMchId, isvCertPath, serialNo, isvMchKey));//??????????????????
            request.setAppDownloadUrl(wxMch.getAppDownloadUrl());  //??????????????????

            request.setWebUrl(wxMch.getWebUrl());  //PC????????????
            request.setWebAuthoriationLetter(WxHttpsUtil.uploadFile(isvMchId, isvCertPath, isvMchKey, wxMch.getWebAuthoriationLetter()));//???????????????
            request.setWebAppid(wxMch.getWebAppid());  //PC????????????????????????APPID

            //??????
            request.checkAndSign(wxPayService.getConfig());
            String reqUrl = "https://api.mch.weixin.qq.com/applyment/micro/submitupgrade";
            String responseContent = wxPayService.post(reqUrl, request.toXML(), true);
            WxPayCommonResult wxResult = WxPayCommonResult.fromXML(responseContent, WxPayCommonResult.class);
            wxResult.checkResult(wxPayService, request.getSignType(), true);
            result.put("isSuccess", true);

        } catch (WxPayException e) {
            _log.error("{}wxError:{}", logPrefix, e);

            result.put("isSuccess", false);
            result.put("errMsg", e.getReturnMsg() + "|" + e.getErrCodeDes());

        }catch (Exception e) {
            _log.error("{}error:{}", logPrefix, e);

            result.put("isSuccess", false);
            result.put("errMsg", "??????????????????????????????????????????????????????, e=" + e.getMessage());
        }

        return result;
    }

    /** ?????? - ??????????????????????????????
     * return JSON
     * isSuccess : ??????????????????????????????
     * errMsg??? ???????????? / ?????????????????????
     * status: ??????
     * subMchId:
     * signUrl:
     * **/
    public JSONObject wxQueryApplymentSubmitUpgrade(JSONObject isvParam, WxMchUpgradeSnapshot wxMch) {

        String logPrefix = "????????????????????????????????????";
        JSONObject result = new JSONObject();

        try {

//            if(true){
//                result.put("isSuccess", true);
//                Byte testStatus = MchConstant.WXAPPLY_GENERAL_STATUS_WAIT_ACCOUNT;
//
//                result.put("applyStatus", testStatus);
//                if(testStatus == MchConstant.WXAPPLY_GENERAL_STATUS_ING){  //???????????????
//                    result.put("applymentStateDesc", "?????????????????????");
//
//                }else if(testStatus == MchConstant.WXAPPLY_GENERAL_STATUS_FAIL){  //????????????
//                    result.put("errMsg", "????????????????????????");
//
//                }else if(testStatus == MchConstant.WXAPPLY_GENERAL_STATUS_WAIT_SIGN){  //???????????? ?????????
//                    result.put("signUrl", "https://xxpayvip.oss-cn-beijing.aliyuncs.com/apply/a6b10f8f-7a6e-4860-98f4-d25ebad5ce5b.png");
//
//                }else if(testStatus == MchConstant.WXAPPLY_GENERAL_STATUS_WAIT_ACCOUNT){  //???????????? ??? ?????????????????????
//
//                    String accountInfo = "????????????:[1];" +
//                            "????????????:[2];" +
//                            "????????????:[3];" +
//                            "????????????:[4];" +
//                            "????????????:[5];" +
//                            "????????????:[6];" +
//                            "????????????:[7];" +
//                            "??????????????????:[8];";
//                    result.put("accountVerifyInfo", accountInfo);
//
//
//                }else if(testStatus == MchConstant.WXAPPLY_GENERAL_STATUS_OK){  //????????????
//                }
//
//                return result;
//            }


            String isvMchId = isvParam.getString("mchId");
            String isvMchKey = isvParam.getString("key");

            String isvCertPath = payConfig.getUploadIsvCertRootDir() + File.separator + isvParam.getString("cert");
            _log.debug("{}mainParam={}", logPrefix, isvParam);
            WxPayConfig wxPayConfig = new WxPayConfig();
            wxPayConfig.setMchId(isvMchId);
            wxPayConfig.setMchKey(isvMchKey);
            wxPayConfig.setKeyPath(isvCertPath);
            wxPayConfig.setSignType(WxPayConstants.SignType.HMAC_SHA256);

            WxPayService wxPayService = new WxPayServiceImpl();
            wxPayService.setConfig(wxPayConfig); // ??????????????????

            WxQueryApplymentSubmitUpgradeRequest request = new WxQueryApplymentSubmitUpgradeRequest();
            request.setVersion("1.0"); //?????????????????? 1.0
            request.setSubMchId(wxMch.getSubMchId()); //???????????????

            //??????
            request.checkAndSign(wxPayService.getConfig());
            String reqUrl = "https://api.mch.weixin.qq.com/applyment/micro/getupgradestate";
            String responseContent = wxPayService.post(reqUrl, request.toXML(), true);
            WxPayCommonResult wxResult = WxPayCommonResult.fromXML(responseContent, WxPayCommonResult.class);
            wxResult.checkResult(wxPayService, request.getSignType(), true);
            Map resultMap = wxResult.toMap();

            String applyMentState = (String)resultMap.get("applyment_state");

            if("CHECKING".equals(applyMentState) || "AUDITING".equals(applyMentState)){ //?????????
                result.put("applyStatus", MchConstant.WXAPPLY_GENERAL_STATUS_ING);
                result.put("applymentStateDesc", resultMap.get("applymentStateDesc"));

            }

            if("REJECTED".equals(applyMentState)){ //?????????
                result.put("applyStatus", MchConstant.WXAPPLY_GENERAL_STATUS_FAIL);
                result.put("errMsg", resultMap.get("audit_detail").toString());
            }

            if("FROZEN".equals(applyMentState)){ //?????????
                result.put("applyStatus", MchConstant.WXAPPLY_GENERAL_STATUS_FAIL);
                result.put("errMsg", "?????????");
            }

            if("NEED_SIGN".equals(applyMentState)){ //?????????
                result.put("applyStatus", MchConstant.WXAPPLY_GENERAL_STATUS_WAIT_SIGN);
                result.put("signUrl", resultMap.get("sign_qrcode").toString());
            }
            if("ACCOUNT_NEED_VERIFY".equals(applyMentState)){ //???????????????
                result.put("applyStatus", MchConstant.WXAPPLY_GENERAL_STATUS_WAIT_ACCOUNT);

                String accountInfo = "????????????:["+result.getString("account_name")+"];" +
                        "????????????:["+result.getString("pay_amount")+"];" +
                        "????????????:["+result.getString("destination_account_number")+"];" +
                        "????????????:["+result.getString("destination_account_name")+"];" +
                        "????????????:["+result.getString("destination_account_bank")+"];" +
                        "????????????:["+result.getString("city")+"];" +
                        "????????????:["+result.getString("remark")+"];" +
                        "??????????????????:["+result.getString("deadline_time")+"];";
                result.put("accountVerifyInfo", accountInfo);
            }

            if("FINISH".equals(applyMentState)){ //??????
                result.put("applyStatus", MchConstant.WXAPPLY_GENERAL_STATUS_OK);
                result.put("signUrl", resultMap.get("sign_qrcode").toString());
            }

            result.put("isSuccess", true);
        } catch (WxPayException e) {
            _log.error("{}wxError:{}", logPrefix, e);

            result.put("isSuccess", false);
            result.put("errMsg", e.getReturnMsg() + "|" + e.getErrCodeDes());

        }catch (Exception e) {
            _log.error("{}error:{}", logPrefix, e);
            result.put("isSuccess", false);
            result.put("errMsg", "??????????????????????????????????????????????????????, e=" + e.getMessage());
        }

        return result;
    }



    /** ?????? - ??????????????????
     * return JSON { isSuccess: false, errMsg: '', applymentId: '123'}
     * **/
    public JSONObject wxMicroApplymentSubmit(JSONObject isvParam, WxMchSnapshot wxMchSnapshot) {

        String logPrefix = "????????????????????????";
        JSONObject result = new JSONObject();

        try {

            String isvMchId = isvParam.getString("mchId");
            String isvMchKey = isvParam.getString("key");
            String mchV3Key = isvParam.getString("apiV3Key");
            String serialNo = isvParam.getString("serialNo");

            String isvCertPath = payConfig.getUploadIsvCertRootDir() + File.separator + isvParam.getString("cert");
            _log.debug("{}mainParam={}", logPrefix, isvParam);

            String apiClientKeyPath = payConfig.getUploadIsvCertRootDir() + File.separator + isvParam.getString("apiClientKey");
            _log.debug("{}mainParam={}", logPrefix, isvParam);

            PrivateKey mchPrivateKey = PemUtil.loadPrivateKey(new FileInputStream(apiClientKeyPath));
            _log.debug("{}mchPrivateKey={}", logPrefix, mchPrivateKey);

            //????????????????????????
            JSONObject certJson = getCertificate(isvMchId, apiClientKeyPath, serialNo, mchV3Key);
            if(!certJson.getBoolean("isSuccess")){
                throw new IOException("??????????????????????????????");
            }

            String wechatPaySerialNo = certJson.getString("wechatPaySerialNo");
            List<X509Certificate> certificates = (List<X509Certificate>) certJson.get("certs");
            X509Certificate wxCert = certificates.get(0);

            //????????????
            JSONObject request = new JSONObject();
            request.put("out_request_no", isvMchId + "_" +wxMchSnapshot.getApplyNo()); //??????????????????, ?????????????????????????????????????????????????????????????????????

            //?????????????????????
            JSONObject contactInfo = new JSONObject();
            contactInfo.put("contact_type", wxMchSnapshot.getContactType());//?????????????????????
            contactInfo.put("contact_name", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getContact(), wxCert));//?????????????????????
            contactInfo.put("contact_id_card_number", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getIdCardNumber(), wxCert));//???????????????????????????
            contactInfo.put("mobile_phone", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getContactPhone(), wxCert));//????????????
            contactInfo.put("contact_email", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getContactEmail(), wxCert));//???????????????
            request.put("contact_info", contactInfo);

            //????????????
            request.put("organization_type", wxMchSnapshot.getOrganizationType()); //????????????
            request.put("id_doc_type", "IDENTIFICATION_TYPE_MAINLAND_IDCARD"); //?????????/?????????????????????????????????????????????

            //???????????????
            JSONObject idCardInfo = new JSONObject();
            idCardInfo.put("id_card_copy", WxHttpsUtil.uploadFile(isvMchId, isvCertPath, isvMchKey, wxMchSnapshot.getIdCardCopy()));//????????????????????????
            idCardInfo.put("id_card_national", WxHttpsUtil.uploadFile(isvMchId, isvCertPath, isvMchKey, wxMchSnapshot.getIdCardNational()));//????????????????????????
            idCardInfo.put("id_card_name", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getIdCardName(), wxCert));//???????????????
            idCardInfo.put("id_card_number", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getIdCardNumber(), wxCert));//???????????????
            idCardInfo.put("id_card_valid_time", wxMchSnapshot.getIdCardValidEndTime());//??????????????????????????????
            request.put("id_card_info", idCardInfo);

            request.put("need_account_info", true);//??????????????????????????????

            //??????????????????
            JSONObject accountInfo = new JSONObject();
            accountInfo.put("bank_account_type", wxMchSnapshot.getBankAccountType());//????????????
            accountInfo.put("account_name", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getAccountName(), wxCert));//????????????
            accountInfo.put("account_bank", wxMchSnapshot.getAccountBank());//????????????
            accountInfo.put("bank_address_code", wxMchSnapshot.getBankAddressCode());//????????????????????????
            accountInfo.put("bank_name", wxMchSnapshot.getBankName());//??????????????????????????????)
            accountInfo.put("account_number", RSAEncryptUtil.rsaEncrypt(wxMchSnapshot.getAccountNumber(), wxCert));//????????????

            request.put("account_info", accountInfo);

            //????????????
            JSONObject salesSceneInfo = new JSONObject();
            salesSceneInfo.put("store_name", wxMchSnapshot.getStoreName());//????????????
            salesSceneInfo.put("store_url", "http://www.qq.com");//????????????
            request.put("sales_scene_info", salesSceneInfo);

            request.put("merchant_shortname", wxMchSnapshot.getMerchantShortName());//????????????
            request.put("business_addition_desc", wxMchSnapshot.getBusinessAdditionDesc());//????????????

            //????????????
            if(StringUtils.isNotEmpty(wxMchSnapshot.getBusinessAdditionPics())){
                JSONArray additionPics = new JSONArray();
                additionPics.add(WxHttpsUtil.uploadFile(isvMchId, isvCertPath,isvMchKey, wxMchSnapshot.getBusinessAdditionPics()));
                request.put("business_addition_pics", additionPics.toJSONString());
            }

            //????????????
            if(StringUtils.isNotEmpty(wxMchSnapshot.getQualifications())){
                String[] qualificationsList = wxMchSnapshot.getQualifications().split(",");

                JSONArray qualifications = new JSONArray();
                for (String str : qualificationsList) {
                    qualifications.add(WxHttpsUtil.uploadFile(isvMchId, isvCertPath, isvMchKey, str));
                }
                request.put("qualifications", qualifications);
            }


            //????????????
            String reqUrl = "https://api.mch.weixin.qq.com/v3/ecommerce/applyments/";

            HttpPost httpPost = new HttpPost(reqUrl);
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.addHeader("Accept", "application/json");
            httpPost.addHeader("Wechatpay-Serial", wechatPaySerialNo);

            StringEntity postingString = new StringEntity(request.toJSONString(), "utf-8");
            httpPost.setEntity(postingString);

            _log.info("=====????????????====" + request.toJSONString());

            WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
                    .withMerchant(isvMchId, serialNo, PemUtil.loadPrivateKey(new FileInputStream(apiClientKeyPath)))
                    .withWechatpay(certificates);

            CloseableHttpClient httpClient = builder.build();

            CloseableHttpResponse response = httpClient.execute(httpPost);

            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity());
            _log.info("=====response_body=====" + body);

            JSONObject res = JSON.parseObject(body);

            if (statusCode == 200) {
                String applymentId = res.getString("applyment_id");

                result.put("isSuccess", true);
                result.put("applymentId", applymentId);
            } else {
                _log.info("apply failed,resp code=" + statusCode);
                throw new IOException("request failed " + "[" + res.getString("message") + "]");
            }
        } /*catch (Exception e) {
            _log.error("{}wxError:{}", logPrefix, e);

            result.put("isSuccess", false);
            result.put("errMsg", e.getReturnMsg() + "|" + e.getErrCodeDes());

        }*/catch (Exception e) {
            _log.error("{}error:{}", logPrefix, e);

            result.put("isSuccess", false);
            result.put("errMsg", "??????????????????????????????????????????????????????, e=" + e.getMessage());
        }

        return result;
    }


    /** ?????? - ????????????????????????????????????
     * return JSON
     * isSuccess : ??????????????????????????????
     * errMsg??? ???????????? / ?????????????????????
     * status: ??????
     * subMchId:
     * signUrl:
     * **/
    public JSONObject wxQueryMicroApplymentSubmit(JSONObject isvParam, WxMchSnapshot wxMchSnapshot) {

        String logPrefix = "????????????????????????????????????";
        JSONObject result = new JSONObject();

        try {

            String isvMchId = isvParam.getString("mchId");
            String mchV3Key = isvParam.getString("apiV3Key");
            String serialNo = isvParam.getString("serialNo");

            String apiClientKeyPath = payConfig.getUploadIsvCertRootDir() + File.separator + isvParam.getString("apiClientKey");
            _log.debug("{}mainParam={}", logPrefix, isvParam);

            //????????????????????????
            JSONObject certJson = getCertificate(isvMchId, apiClientKeyPath, serialNo, mchV3Key);
            if(!certJson.getBoolean("isSuccess")){
                throw new IOException("??????????????????????????????");
            }

            String wechatPaySerialNo = certJson.getString("wechatPaySerialNo");
            List<X509Certificate> certificates = (List<X509Certificate>) certJson.get("certs");
            X509Certificate wxCert = certificates.get(0);

            String outRequestNo = isvMchId + "_" +wxMchSnapshot.getApplyNo(); //??????????????????, ?????????????????????????????????????????????????????????????????????

            //????????????
            String reqUrl = "https://api.mch.weixin.qq.com/v3/ecommerce/applyments/out-request-no/" + outRequestNo;

            HttpGet httpGet = new HttpGet(reqUrl);
            httpGet.addHeader("Content-Type", "application/json");
            httpGet.addHeader("Accept", "application/json");
            httpGet.addHeader("Wechatpay-Serial", wechatPaySerialNo);

            WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
                    .withMerchant(isvMchId, serialNo, PemUtil.loadPrivateKey(new FileInputStream(apiClientKeyPath)))
                    .withWechatpay(certificates);

            CloseableHttpClient httpClient = builder.build();

            CloseableHttpResponse response = httpClient.execute(httpGet);

            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity());
            if (statusCode == 200) {
                _log.info("=====body=====" + body);
                JSONObject res = JSON.parseObject(body);
                String applyMentState = res.getString("applyment_state");

                if("CHECKING".equals(applyMentState)){ //???????????????
                    result.put("applyStatus", MchConstant.WXAPPLY_MICRO_STATUS_EDITING);
                    result.put("applymentStateDesc", res.getString("applyment_state_msg"));
                }

                if("AUDITING".equals(applyMentState)){ //?????????
                    result.put("applyStatus", MchConstant.WXAPPLY_MICRO_STATUS_ING);
                    result.put("applymentStateDesc", res.getString("applyment_state_msg"));
                }

                if("REJECTED".equals(applyMentState)){ //?????????
                    result.put("applyStatus", MchConstant.WXAPPLY_MICRO_STATUS_FAIL);
                    result.put("applymentStateDesc", res.getString("applyment_state_msg"));

                    JSONArray auditDetail = JSONArray.parseArray(res.getString("audit_detail"));
                    result.put("errMsg", auditDetail.getJSONObject(0).getString("reject_reason"));
                }

                if("ACCOUNT_NEED_VERIFY".equals(applyMentState)){ //???????????????
                    result.put("applymentStateDesc", res.getString("applyment_state_msg"));
                    String legalValidationUrl = res.getString("legal_validation_url") == null ? null : res.getString("legal_validation_url");
                    result.put("applyStatus", MchConstant.WXAPPLY_MICRO_STATUS_WAIT_VERIFY);
                    result.put("legalValidationUrl", legalValidationUrl);
                    String imgUrl =  payConfig.getPayUrl() + "/qrcode_img_get?url=" + URLEncoder.encode(legalValidationUrl);
                    result.put("wxApplymentMchQrImg", imgUrl);
                }

                if("NEED_SIGN".equals(applyMentState)){ //?????????
                    result.put("applymentStateDesc", res.getString("applyment_state_msg"));
                    String singUrl = res.getString("sign_url") == null ? null : res.getString("sign_url");
                    result.put("applyStatus", MchConstant.WXAPPLY_MICRO_STATUS_WAIT_SIGN);
                    result.put("signUrl", singUrl);
                    String imgUrl =  payConfig.getPayUrl() + "/qrcode_img_get?url=" + URLEncoder.encode(singUrl);
                    result.put("wxApplymentMchQrImg", imgUrl);
                }

                if("FINISH".equals(applyMentState)){ //??????
                    result.put("applyStatus", MchConstant.WXAPPLY_MICRO_STATUS_OK);
                    result.put("applymentStateDesc", res.getString("applyment_state_msg"));
                    result.put("subMchId", res.getString("sub_mchid"));
                }

                if("FROZEN".equals(applyMentState)){ //?????????
                    result.put("applyStatus", MchConstant.WXAPPLY_MICRO_STATUS_FROZEN);
                    result.put("applymentStateDesc", res.getString("applyment_state_msg"));

                    JSONArray auditDetail = JSONArray.parseArray(res.getString("audit_detail"));
                    result.put("errMsg", auditDetail.getJSONObject(0).getString("reject_reason"));
                }

                result.put("isSuccess", true);
            } else {
                _log.info("download failed,resp code=" + statusCode + ",body=" + body);
                throw new IOException("request failed");
            }
        } catch (Exception e) {
            _log.error("{}error:{}", logPrefix, e);
            result.put("isSuccess", false);
            result.put("errMsg", "??????????????????????????????????????????????????????, e=" + e.getMessage());
        }

        return result;
    }



    /** ?????? - ?????????????????? **/
    private JSONObject getCertificate(String wxMchId, String wxKeyPath, String serialNo, String apiV3key) throws IOException, GeneralSecurityException {

        JSONObject result = new JSONObject();
        result.put("isSuccess", false);

        WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
                .withMerchant(wxMchId, serialNo, PemUtil.loadPrivateKey(new FileInputStream(wxKeyPath)));

        //????????????
        builder.withValidator(response -> true);

        //TODO ???????????????????????????
        /*if (wechatpayCertificatePath == null) {
            //????????????
            builder.withValidator(response -> true);
        } else {
            List<X509Certificate> certs = new ArrayList<>();
            certs.add(PemUtil.loadCertificate(new FileInputStream(wechatpayCertificatePath)));
            builder.withWechatpay(certs);
        }*/

        List<X509Certificate> x509Certs = new ArrayList<>();

        HttpGet httpGet = new HttpGet("https://api.mch.weixin.qq.com/v3/certificates");
        httpGet.addHeader("Accept", "application/json");

        try (CloseableHttpClient client = builder.build()) {
            CloseableHttpResponse response = client.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity());
            _log.info("?????????????????????" + body);

            JSONObject object  = (JSONObject) JSON.parse(body);
            if(StringUtils.isBlank(object.getString("data"))){
                return result;
            }

            if (statusCode == 200) {
                List<CertificateItem> certList = JSONObject.parseArray(object.getString("data"), CertificateItem.class);

                AesUtil decryptor = new AesUtil(apiV3key.getBytes(StandardCharsets.UTF_8));
                for (CertificateItem item : certList) {
                    PlainCertificateItem plainCert = new PlainCertificateItem(
                            item.getSerialNo(), item.getEffectiveTime(), item.getExpireTime(),
                            decryptor.decryptToString(
                                    item.getEncryptCertificate().getAssociatedData().getBytes(StandardCharsets.UTF_8),
                                    item.getEncryptCertificate().getNonce().getBytes(StandardCharsets.UTF_8),
                                    item.getEncryptCertificate().getCiphertext()));

                    ByteArrayInputStream inputStream = new ByteArrayInputStream(plainCert.getPlainCertificate().getBytes(StandardCharsets.UTF_8));
                    X509Certificate x509Cert = PemUtil.loadCertificate(inputStream);
                    x509Certs.add(x509Cert);
                }

                //???????????????????????????????????????????????????????????????????????????????????????????????????????????????
                Verifier verifier = new CertificatesVerifier(x509Certs);
                Validator validator = new WechatPay2Validator(verifier);
                boolean isCorrectCert = validator.validate(response);
                _log.info(isCorrectCert ? "=== validate success ===" : "=== validate failed ===");

                if(isCorrectCert) {
                    result.put("isSuccess", true);
                    result.put("certs", x509Certs);
                    result.put("wechatPaySerialNo", certList.get(0).getSerialNo());
                }

                return result;
            } else {
                _log.info("download failed,resp code=" + statusCode);
                throw new IOException("request failed " + object.getString("message"));
            }
        }
    }

    /*public static void main(String[] args) {
        try {
            downloadCertificate("1493126272", "D:\\cert\\apiclient_key.pem", "580F998D4ACF5DA73ABA9EE8E7A4C06557A1E6B7", "9bEBe7uJr9DNa670748bc65ev8Eytiu9");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
    }*/

    /** ?????? - ???????????????????????? **/
    private JSONObject getCertficatesSerialNo(String wxMchId, String wxKey) {

        String logPrefix = "?????????-?????????????????????";

        try {
            // ?????????????????????
            WxPayConfig wxPayConfig = new WxPayConfig();
            wxPayConfig.setMchId(wxMchId);
            wxPayConfig.setMchKey(wxKey);
            wxPayConfig.setSignType(WxPayConstants.SignType.HMAC_SHA256);  //?????????????????? HMAC-SHA256

            WxPayService wxPayService = new WxPayServiceImpl();
            wxPayService.setConfig(wxPayConfig); // ??????????????????


            WxPayDefaultRequest request = new WxPayDefaultRequest();

            //??????
            request.checkAndSign(wxPayService.getConfig());
            String reqUrl = "https://api.mch.weixin.qq.com/risk/getcertficates";
            String responseContent = wxPayService.post(reqUrl, request.toXML(), false);

            WxPayCommonResult result = WxPayCommonResult.fromXML(responseContent, WxPayCommonResult.class);
            result.checkResult(wxPayService, request.getSignType(), true);

            Map resultMap = result.toMap();
            String certificates = resultMap.get("certificates").toString();
            JSONObject certificatesJSON = JSONObject.parseObject(certificates);
            JSONObject jsonObject = (JSONObject)certificatesJSON.getJSONArray("data").get(0);
            return jsonObject;

        } catch (WxPayException e) {
            _log.error("{}wxError:{}", logPrefix, e);
            throw ServiceException.build(RetEnum.RET_COMM_OPERATION_FAIL);
        }catch (Exception e) {
            _log.error("{}error:{}", logPrefix, e);
            throw ServiceException.build(RetEnum.RET_COMM_UNKNOWN_ERROR);
        }
    }

    /**
     * ???json???????????????json???????????????json????????????????????????????????????
     * @param json
     * @return
     */
    private JSONObject toDefaultJSONObject(String json) {
        if(StringUtils.isEmpty(json)) return new JSONObject();
        return JSONObject.parseObject(json);
    }

    /**
     * ???json???????????????json???????????????json?????????????????????
     * @param json
     * @return
     */
    private JSONObject toJSONObject(String json) {
        if(StringUtils.isEmpty(json)) return null;
        return JSONObject.parseObject(json);
    }


    /** ?????????????????????????????? ???????????????????????? **/
    private String convertImgArray(String imgUrlArr, String isvMchId, String isvCertPath,String serialNo, String isvMchKey) {

        if (StringUtils.isEmpty(imgUrlArr)) {
            return null;
        }

        JSONArray imgArr = JSONArray.parseArray(imgUrlArr);
        if (imgArr.isEmpty()) {
            return null;
        }

        try {
            JSONArray imgMediaIdArr = new JSONArray();
            for (Object imgUrl : imgArr) {
                imgMediaIdArr.add(WxHttpsUtil.uploadFile(isvMchId, isvCertPath,isvMchKey, imgUrl.toString()));
            }
            return imgMediaIdArr.toJSONString();
        } catch (Exception e) {
            e.printStackTrace();
            throw ServiceException.build(RetEnum.RET_COMM_OPERATION_FAIL);
        }
    }
}
