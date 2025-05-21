'use strict';

/**
 * Register controller.
 */
angular.module('docs').controller('Register', ['$scope', '$state', '$dialog', '$translate', 'Restangular',
    function($scope, $state, $dialog, $translate, Restangular) {
        $scope.user = {};
        $scope.translate = function(key, params) {
            return $translate.instant(key, params);
        };

        /**
         * Register a new user.
         */
        $scope.register = function() {
            Restangular.one('user/register').customPOST(
                $.param({
                    username: $scope.user.username,
                    password: $scope.user.password,
                    email: $scope.user.email,
                    storage_quota: $scope.user.storageQuota
                }),
                '',
                {},
                { 'Content-Type': 'application/x-www-form-urlencoded' }
            ).then(function(response) {
                $dialog.messageBox(
                    $scope.translate('register.success_title'),
                    $scope.translate('register.success_message'),
                    [
                        {
                            result: 'ok',
                            label: $scope.translate('ok'),
                            cssClass: 'btn-primary'
                        }
                    ]
                ).result.then(function() {
                    $state.go('login');
                });
            }, function(error) {
                if (error.data.type === 'AlreadyExistingUsername') {
                    $dialog.messageBox(
                        $scope.translate('register.error_title'),
                        $scope.translate('register.error_username_exists'),
                        [
                            {
                                result: 'ok',
                                label: $scope.translate('ok'),
                                cssClass: 'btn-primary'
                            }
                        ]
                    );
                } else {
                    $dialog.messageBox(
                        $scope.translate('register.error_title'),
                        $scope.translate('register.error_message'),
                        [
                            {
                                result: 'ok',
                                label: $scope.translate('ok'),
                                cssClass: 'btn-primary'
                            }
                        ]
                    );
                }
            });
        };
    }
]);