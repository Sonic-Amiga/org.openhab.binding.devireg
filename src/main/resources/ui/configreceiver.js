String.prototype.format = function()
{
    var formatted = this;
    for (var i = 0; i < arguments.length; i++) {
        var regexp = new RegExp('\\{'+i+'\\}', 'gi');
        formatted = formatted.replace(regexp, arguments[i]);
    }
    return formatted;
};

function showProgressIndicator(show)
{
    if (show)
    {
        document.getElementById("main").style.display = 'none';
        document.getElementById("progress").style.display = '';
    }
    else
    {
        document.getElementById("main").style.display = '';
        document.getElementById("progress").style.display = 'none';        
    }
}

function setStatus(text)
{
    document.getElementById("otp").value = "";
    document.getElementById("status").innerHTML = text;
}

function setError(text)
{
    setStatus("<font color='RED'>" + text + "</font>");
}

function receive()
{
    // SecureDeviceGrid OTP consists of only digits. Any dashes are there
    // just for cook look, like a telephone number.
    var otp = document.getElementById("otp").value.replace(/\D/g, "");

    // OpeSDG library validates the OTP itself, but this also catches empty OTP,
    // which would have caused HTTP 404 otherwise due to a completely missing URL part
    if (otp.length < 7)
    {
        setError(bad_otp);
        return;
    }
    
    showProgressIndicator(true);
    
    if (window.XMLHttpRequest) {
        // code for IE7+, Firefox, Chrome, Opera, Safari
        xmlhttp = new XMLHttpRequest();
    } else {
        // code for IE6, IE5
        xmlhttp = new ActiveXObject("Microsoft.XMLHTTP");
    }
    
    xmlhttp.onreadystatechange = function()
    {
        if (this.readyState == 4) {
            readResult(this);
        }
    };
    
    request = "/rest/devireg/receive/" + otp;

    xmlhttp.open("POST", request, true);
    xmlhttp.send();
}

function readResult(response)
{
    var result = JSON.parse(response.responseText);

    showProgressIndicator(false);
    
    if (response.status == 200)
    {
        setStatus(msg_ok.format(result.thingCount));        
    }
    else if (result.error)
    {
        setError(result.error.message);
    }
    else
    {
        setError(msg_error.format(response.status));
    }
}
