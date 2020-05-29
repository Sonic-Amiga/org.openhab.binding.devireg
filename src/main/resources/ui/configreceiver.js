angular
    .module('deviRegApp', ["ngMaterial", "ngMessages", "ngSanitize"])
    .controller('deviRegFormController', deviRegFormController);

function deviRegFormController($scope, $http) {

    $scope.result_success = false;
    $scope.result_error = false;
    $scope.processing = false;

    $scope.processForm = function () {
        $scope.processing = true;
        $http({
            method: 'POST',
            url: '/rest/danfoss/receive/' + $scope.otpFormData.otp,
            data: ''
        })
            .then(function onSuccess(response) {
                // Handle success
                $scope.result_success = 'Successfully received ' + response.data.thingCount + ' things. Please check your <a href="/paperui/#/inbox" class="alert-link">Inbox</a>';
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
