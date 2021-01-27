angular
    .module('deviRegApp', ["ngMaterial", "ngMessages", "ngSanitize"])
    .controller('deviRegFormController', deviRegFormController);

function deviRegFormController($scope, $http) {

    $scope.result_success = false;
    $scope.result_error = false;
    $scope.processing = false;

    $scope.processForm = function () {
        // SecureDeviceGrid OTP consists of only digits. Any dashes are present there
        // just for cook look, like a telephone number.
        var otp = $scope.otpFormData.otp.replace(/\D/g, "");
        $scope.processing = true;
        $http({
            method: 'POST',
            url: '/rest/danfoss/receive/' + otp,
            data: ''
        })
            .then(function onSuccess(response) {
                // Handle success
                $scope.result_success = 'Successfully received ' + response.data.thingCount + ' things. Please check your <a href="/#!/settings/things/inbox" class="alert-link">Inbox</a>';
				$scope.result_error = '';
                $scope.processing = false;
            })
            .catch(function onError(response) {
                // Handle error
				$scope.result_success = '';
                $scope.result_error = '<strong>Error</strong>: ' + response.data.error.message;
                $scope.processing = false;
            });
    };
}
