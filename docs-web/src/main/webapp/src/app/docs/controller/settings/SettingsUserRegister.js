'use strict';

angular.module('docs').controller('SettingsUserRegister', function($scope, $dialog, $translate, Restangular) {
    $scope.registrationRequests = [];

    $scope.translate = function(key, params) {
        return $translate.instant(key, params);
    };

    $scope.loadRequests = function() {
        Restangular.one('user/register').get().then(function(data) {
            $scope.registrationRequests = data.requests.map(function(r) {
                return {
                    id: r.id,
                    username: r.username,
                    email: r.email,
                    createDate: new Date(r.createDate)
                };
            });
        });
    };

    $scope.approve = function(request) {
        if (window.confirm($scope.translate('settings.user.approve_confirm_message', { username: request.username }))) {
            Restangular.one('user/register', request.id).post('', { action: 'approve' }).then(function() {
                $scope.loadRequests();
            });
        }
    };

    $scope.reject = function(request) {
        if (window.confirm($scope.translate('settings.user.reject_confirm_message', { username: request.username }))) {
            Restangular.one('user/register', request.id).post('', { action: 'reject' }).then(function() {
                $scope.loadRequests();
            });
        }
    };

    $scope.loadRequests();
});
