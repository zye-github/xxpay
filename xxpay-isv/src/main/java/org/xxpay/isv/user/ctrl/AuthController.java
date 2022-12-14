package org.xxpay.isv.user.ctrl;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xxpay.core.common.Exception.ServiceException;
import org.xxpay.core.common.annotation.MethodLog;
import org.xxpay.core.common.constant.Constant;
import org.xxpay.core.common.constant.MchConstant;
import org.xxpay.core.common.constant.RetEnum;
import org.xxpay.core.common.domain.BizResponse;
import org.xxpay.core.common.domain.XxPayResponse;
import org.xxpay.core.common.util.*;
import org.xxpay.core.common.vo.JWTBaseUser;
import org.xxpay.core.common.vo.JWTPayload;
import org.xxpay.core.common.vo.JWTUtils;
import org.xxpay.core.entity.IsvInfo;
import org.xxpay.core.entity.SysUser;
import org.xxpay.isv.common.ctrl.BaseController;
import org.xxpay.isv.common.service.RpcCommonService;
import org.xxpay.isv.common.util.AliSmsUtil;
import org.xxpay.isv.user.service.UserService;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@RequestMapping(Constant.ISV_CONTROLLER_ROOT_PATH)
@RestController
public class AuthController extends BaseController {

    @Autowired
    private UserService userService;

    @Autowired
    private RpcCommonService rpcCommonService;
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserDetailsService userDetailsService;

    private static final MyLog _log = MyLog.getLog(AuthController.class);

    /**
     * ????????????
     * @return
     * @throws AuthenticationException
     */
    @RequestMapping(value = "/auth")
    @MethodLog( remark = "??????" )
    public ResponseEntity<?> authToken() throws AuthenticationException{

        String loginType = getValString("loginType");
        String vercodeRandomStr = null;
        if (MchConstant.LOGIN_TYPE_WEB.equals(loginType)) {
            String vercode = getValStringRequired( "vercode"); //???????????????
            vercodeRandomStr = getValStringRequired( "vercodeRandomStr"); //??????????????? ??????key

            String vercodeCacheValue = stringRedisTemplate.opsForValue().get(MchConstant.CACHEKEY_VERCODE_PREFIX_ISV + vercodeRandomStr);

            if(!vercode.equals(vercodeCacheValue)){
                return ResponseEntity.ok(BizResponse.build(RetEnum.RET_COMM_VERCODE_ERROR));
            }
        }

        String username = getValStringRequired( "username");
        String password = getValStringRequired( "password");

        try {
            String token = userService.login(username, password, loginType, null);

            //?????????app???????????????token????????????
            if (StringUtils.isNotBlank(loginType) && (MchConstant.LOGIN_TYPE_APP.equals(loginType))) {
                stringRedisTemplate.opsForValue().set(MchConstant.CACHEKEY_TOKEN_PREFIX_ISV + token, "1",
                        MchConstant.APP_CACHE_TOKEN_TIMEOUT, TimeUnit.SECONDS);
                JSONObject data = new JSONObject();
                data.put("access_token", token);
                return ResponseEntity.ok(XxPayResponse.buildSuccess(data));
            }

            //web??????,???????????????????????????
            if (MchConstant.LOGIN_TYPE_WEB.equals(loginType)) {
                stringRedisTemplate.delete(MchConstant.CACHEKEY_TOKEN_PREFIX_ISV + vercodeRandomStr);
            }
            stringRedisTemplate.opsForValue().set(MchConstant.CACHEKEY_TOKEN_PREFIX_ISV + token, "1",
                    MchConstant.CACHE_TOKEN_TIMEOUT, TimeUnit.SECONDS);
            JSONObject data = new JSONObject();
            data.put("access_token", token);
            return ResponseEntity.ok(XxPayResponse.buildSuccess(data));
        }catch (ServiceException e) {
            return ResponseEntity.ok(BizResponse.build(e.getRetEnum()));
        }
    }

    /**
     * ????????????
     * @return
     */
    @RequestMapping(value = "/auth/register")
    public ResponseEntity<?> register() {

        IsvInfo isvInfo = getObject(IsvInfo.class);
        String mobile = getValStringRequired("mobile");
        String verifyCode = getValStringRequired("vercode");
        String password = getValStringRequired("password");

        //?????????????????????????????????
        if(!XXPayUtil.checkLoginUserName(isvInfo.getLoginUserName())){
            throw ServiceException.build(RetEnum.RET_SERVICE_LOGINUSERNAME_ERROR);
        }

        // ???????????????????????????
        if(rpcCommonService.rpcIsvInfoService.count(isvInfo.lambda().eq(IsvInfo::getMobile, isvInfo.getMobile())) > 0){
            throw ServiceException.build(RetEnum.RET_MGR_MOBILE_EXISTS);
        }
        // ?????????????????????????????????
        if(rpcCommonService.rpcIsvInfoService.count(isvInfo.lambda().eq(IsvInfo::getLoginUserName, isvInfo.getLoginUserName())) > 0){
            throw ServiceException.build(RetEnum.RET_MGR_USERNAME_EXISTS);
        }

        // ????????????????????????
        if(rpcCommonService.rpcIsvInfoService.count(isvInfo.lambda().eq(IsvInfo::getEmail, isvInfo.getEmail())) > 0){
            throw ServiceException.build(RetEnum.RET_MGR_EMAIL_EXISTS);
        }

        //???????????????????????????
        SysUser sysUser = rpcCommonService.rpcSysService.findByMchIdAndMobile(MchConstant.INFO_TYPE_ISV, mobile);
        if (sysUser != null) {
            return ResponseEntity.ok(XxPayResponse.build(RetEnum.RET_MCH_MOBILE_USED));
        }

        //?????????????????????
        if(!verifyMobileCode(mobile, verifyCode, MchConstant.MSGCODE_BIZTPYE_MCH_REGISTER)) {
            return ResponseEntity.ok(BizResponse.build(RetEnum.RET_COMM_VERCODE_ERROR));
        }

        isvInfo.setRegisterPassword(SpringSecurityUtil.generateSSPassword(password));
        isvInfo.setStatus(MchConstant.STATUS_AUDIT_ING); //??????????????????
        rpcCommonService.rpcIsvInfoService.addIsv(isvInfo);

        //??????????????????????????????
        stringRedisTemplate.delete(MchConstant.CACHEKEY_SMSCODE_PREFIX_ISV + MchConstant.MSGCODE_BIZTPYE_MCH_REGISTER + mobile);

        return ResponseEntity.ok(XxPayResponse.buildSuccess());
    }

    /**
     * ????????????(???????????????????????????????????????)
     * @return
     * @throws AuthenticationException
     */
    @RequestMapping(value = "/mgr_auth")
    public ResponseEntity<?> mgrAuthToken() throws AuthenticationException{
        Long isvId = getValLongRequired( "isvId");
        String token = getValStringRequired( "token");

        IsvInfo isvInfo = userService.findByIsvId(isvId);
        if(isvInfo == null) {
            return ResponseEntity.ok(BizResponse.build(RetEnum.RET_ISV_NOT_EXIST));
        }
        if(MchConstant.PUB_YES != isvInfo.getStatus()) {
            return ResponseEntity.ok(BizResponse.build(RetEnum.RET_ISV_STATUS_STOP));
        }

        // ?????????????????????????????????token,????????????
        // ?????????ID+????????????????????????+?????? ???32???MD5???????????????
        String loginUserName = isvInfo.getLoginUserName();
        String secret = "Abc%$G&!!!128G";
        
		String signNowTime = DateUtil.date2Str(new Date(), "ddHHmm"); //????????????
		String rawTokenByNow = isvId + loginUserName + secret + signNowTime;
		String md5Now = MD5Util.string2MD5(rawTokenByNow).toUpperCase();
		
		String sign1Time = DateUtil.date2Str(DateUtil.minusDateByMinute(new Date(), 1 ), "ddHHmm"); //??????????????????1min
		String rawTokenBySub1 = isvId + loginUserName + secret + sign1Time;
        String md5BySub1 = MD5Util.string2MD5(rawTokenBySub1).toUpperCase();
        
        //?????????????????? ??? ???????????????1???????????????????????????59s ???????????????????????????
        if(!md5Now.equals(token) && !md5BySub1.equals(token)) {
            return ResponseEntity.ok(BizResponse.build(RetEnum.RET_MCH_ILLEGAL_LOGIN));
        }

        //????????????
        JWTBaseUser jwtBaseUser = (JWTBaseUser) userDetailsService.loadUserByUsername(isvId.toString());
        jwtBaseUser.setLoginTypeAndVersion(MchConstant.LOGIN_TYPE_WEB, null, null); //??????????????????
        String authToken = JWTUtils.generateToken(new JWTPayload(jwtBaseUser), mainConfig.getJwtSecret(), mainConfig.getJwtExpiration());
        stringRedisTemplate.opsForValue().set(MchConstant.CACHEKEY_TOKEN_PREFIX_ISV + authToken, "1",
										MchConstant.CACHE_TOKEN_TIMEOUT, TimeUnit.SECONDS);
        
        JSONObject data = new JSONObject();
        data.put("access_token", authToken);
        return ResponseEntity.ok(XxPayResponse.buildSuccess(data));
    }


    /**
     * ?????????????????????
     */
    @RequestMapping(value = "/auth/vercode")
    public void getAuthCode(HttpServletResponse response, String vercodeRandomStr) throws IOException {
    	
    	if(StringUtils.isEmpty(vercodeRandomStr))return ;
        Map<String, Object> randomMap = RandomValidateCodeUtil.getRandcode(120, 40, 6, 20);
        String vercodeValue = randomMap.get("randomString").toString(); //??????
        stringRedisTemplate.opsForValue().set(MchConstant.CACHEKEY_VERCODE_PREFIX_ISV + vercodeRandomStr,
        									  vercodeValue, MchConstant.CACHEKEY_VERCODE_TIMEOUT, TimeUnit.MINUTES);
        BufferedImage randomImage = (BufferedImage)randomMap.get("randomImage");
        ImageIO.write(randomImage, "JPEG", response.getOutputStream());
    }


    /**
     * ??????????????????
     * @return
     */
    @RequestMapping(value = "/auth/sms_send")
    public ResponseEntity<?> sendSms(){

        Byte bizType = getValByteRequired("bizType");//???????????? 22-??????

        // ????????????
        if (bizType != MchConstant.MSGCODE_BIZTPYE_MCH_REGISTER) {
            return ResponseEntity.ok(BizResponse.build(RetEnum.RET_MCH_MOBILE_SEND_ERROR));
        }

        String mobile = getValStringRequired("mobile");//?????????
        if(!StrUtil.checkMobileNumber(mobile)) {
            return ResponseEntity.ok(BizResponse.build(RetEnum.RET_MCH_MOBILE_FORMAT_ERROR));
        }

        //???????????????????????????
        SysUser sysUser = rpcCommonService.rpcSysService.findByMchIdAndMobile(MchConstant.INFO_TYPE_ISV, mobile);
        // ?????????????????????????????????????????????????????????
        if (sysUser != null){
            return ResponseEntity.ok(BizResponse.build(RetEnum.RET_MCH_MOBILE_USED));
        }

        // ??????????????????
        String verifyCode = String.valueOf(new Random().nextInt(899999) + 100000);
        logger.info("????????????{}?????????????????????{}", mobile, verifyCode);

        String sysConfigStr = rpcCommonService.rpcSysConfigService.getVal("smsConfig");
        if(!AliSmsUtil.sendSms(Long.parseLong(mobile), verifyCode, bizType, sysConfigStr)){
            return ResponseEntity.ok(BizResponse.build(RetEnum.RET_MCH_MOBILE_SEND_ERROR));
        }

        // ????????????????????????redis
        stringRedisTemplate.opsForValue().set(MchConstant.CACHEKEY_SMSCODE_PREFIX_ISV + bizType + mobile, verifyCode,
                MchConstant.CACHEKEY_SMSCODE_TIMEOUT, TimeUnit.MINUTES);

        return ResponseEntity.ok(BizResponse.buildSuccess());
    }


    /**
     * ?????????????????????
     * @param mobile
     * @param verifyCode
     * @return
     */
    public boolean verifyMobileCode(String mobile, String verifyCode, Byte bizType) {

        if (StringUtils.isAnyBlank(mobile, verifyCode)) {
            return false;
        }

        //?????????????????????
        String redisCode = stringRedisTemplate.opsForValue().get(MchConstant.CACHEKEY_SMSCODE_PREFIX_ISV + bizType + mobile);
        if (verifyCode.equals(redisCode)) {
            return true;
        }

        return false;
    }

}
