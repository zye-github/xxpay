<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="ie=edge">
    <title>่ๅๆฏไป</title>
    <style>
        * {
            padding: 0;
            margin: 0;
        }
        body {
            background: #f0eff4;
        }
        .wrapper {
            /* width: 100%; */
            background: #f0eff4;
            padding: 20px 10px;
        }
        .header {
            position: relative;
            text-align: center;
            margin-bottom: 40px;;
        }

        .header img {
            position: absolute;
            left: 0;
            right: 0;
            margin:auto;
        }
        .main-wrap {
            border: 1px solid #ddd;
            background-color: #fff;
            padding: 50px 10px 15px;
            text-align: center;
        }
        .main-wrap h4 {
            padding-bottom: 15px;
            border-bottom: 1px dashed #ddd;
        }
        .clearFix:after,
        .clearFix:before {
            content: '';
            clear: both;
            display: block;
        }
        .main-wrap .wap-form {
            width: 100%;
            padding-top: 15px;
            /* border:1px solid #f00; */
            text-align: left;
            line-height: 1.5;

        }
        .main-wrap .wap-form label {
            color: #a5a5a5;
            float: left;
            font-size: 1rem;
        }
        .main-wrap .wap-form .shuru {
            width: 70%;
            float: right;
            border: none;
            text-align: right;
            padding: 5px;
        }
        .main-wrap .wap-form input:focus {
            border: none;
        }
        p.beizhu {
            text-align: right;
            color: #7694ca;
            font-size: 12px;
            margin-top: 10px;
        }
        p.beizhu input {
            border: none;
            display: none;
            padding: 5px 0;
            width: 100%;
            background: #f0eff4;
            text-align: right;
        }
        textarea {
            text-decoration: none;
            color: #7694ca;
            font-size: 12px;
            border: none;
            text-align: right;
            width: 100%;
        }
        .blank50 {
            height: 50px;
        }
        .xybank img {
            max-width: 100%;
            vertical-align: middle
        }
        .xybank span {
            padding-left: 5px;
            border-left: 1px solid #ddd;
            font-size: 12px;
            color: #676767;
        }
    </style>
</head>
<body>
<div id='zf'>

    <form action='/api/qrcode/toPay' id="myForm" method="post">
        <input hidden name="channelUserId" value="${channelUserId!}"/>
        <input hidden name="tradeOrderId" value="${reqParam.tradeOrderId!}"/>
        <input hidden name="mchId" value="${reqParam.mchId!}"/>
        <input hidden name="storeId" value="${reqParam.storeId!}"/>
        <input hidden name="operatorId" value="${reqParam.operatorId!}"/>
        <input hidden name="isBalancePay" value="0"/> <!--ๆฏๅฆไฝ้ขๆฏไป๏ผ 1ๆฏ๏ผ0ๅฆ -->
        <div class="wrapper">
            <div class="header">
                <img src="/images/logo.png" alt="">
            </div>
            <div class="main-wrap">

                <#if mchInfo ?? >
                    <h4>ๅ&nbsp; ${mchInfo.name!} &nbsp;ไปๆฌพ</h4>
                </#if>

                <#if storeInfo ?? >
                    <h4>้จๅบ๏ผ &nbsp; ${storeInfo.storeName!} </h4>
                </#if>

                <#if operatorInfo ?? >
                    <h4>ๅบๅ๏ผ&nbsp; ${operatorInfo.userName!} </h4>
                </#if>

                <#if memberCouponList ?? >
                    <select name="memberCouponNo">
                        <option value="">ไธไฝฟ็จไผๆ?ๅธ</option>
                     <#list memberCouponList! as memberCoupon>
                         <option value="${memberCoupon.couponNo}">${memberCoupon.ps.mchCoupon.couponName!}</option>
                     </#list>
                     </select>
                </#if>

                <div class="wap-form clearFix">
                    <label for="">้้ข</label>

                    <#if reqParam.payAmount ?? && reqParam.payAmount!="" >
                        <input class="shuru" name="amount" value="${reqParam.payAmount?number / 100}" v-model="something" >
                    <#else>
                        <input class="shuru" name="amount" value="" v-model="something" >
                    </#if>

                </div>
            </div>
            <p class="beizhu11">
                <span>ๆทปๅ?ไปๆฌพๅคๆณจ</span>
                <input type="text">
            </p>


            <button class="wxPayBtn" onclick="javascript:return false;" style="width:90%; background-color:green; margin-top:20px; height:40px">ๅพฎไฟกๆฏไป</button>
            <br/>

            <#if memberBalance ?? >
                <button class="balancePayBtn" onclick="javascript:return false;" style="width:90%; background-color:lightskyblue; margin-top:20px; height:40px">ไฝ้ขๆฏไป(ๅฏ็จไฝ้ข๏ผ ${memberBalance.balance/100}ๅ)</button>
            <#else>
                <button  onclick="javascript:return false;" style="width:90%; background-color:lightseagreen; margin-top:20px; height:40px">ๅ?ๅฅไผๅ</button>
            </#if>

            <div class="blank50"></div>
        </div>
    </form>

</div>
<script src="https://cdn.bootcss.com/jquery/2.2.3/jquery.js"></script>
<script src="https://cdn.bootcss.com/vue/2.5.13/vue.js"></script>
<script>

    //ๅฝ็นๅปๆทปๅ?ๅคๆณจ็ๆถๅ
    $('.beizhu').click(function(){
        $('.beizhu input').focus();
        $('.beizhu input').css({"display":"block","outline":'none'})

        $('.beizhu span').css('display','none');
    })

    $(function(){
        $('.shuru').focus().css('outline','none');

        var payAmount = $('input[name="amount"]').val();
        if(payAmount){ //ๅบๅฎ้้ข
            $('input[name="amount"]').attr('readonly', 'readonly');
        }
    });

    $('.wxPayBtn').click(function(){
        $('#myForm')[0].submit();
    });

    $('.balancePayBtn').click(function(){

        $('input[name="isBalancePay"]').val("1");
        $('#myForm')[0].submit();
    });

    var html = '';


    //ๅผๅงๆฏไป
    function paymentOrder(amount){

        $('#myForm').submit();
        return false;


    }

</script>
<script src="//cdn.bootcss.com/jquery-weui/1.0.1/js/jquery-weui.min.js"></script>
</body>
</html>