'use strict';

/**
 * Main controller.
 */
angular.module('docs').controller('Main', function($scope, $rootScope, $state, User) {
  User.userInfo().then(function(data) {
    if (data.anonymous) {
      if($state.current.name !== 'login') {
        $state.go('login', {}, {
          location: 'replace'
        });
      }
    } else {
      if($state.current.name !== 'document.default') {
        $state.go('document.default', {}, {
          location: 'replace'
        });
      }
    }
  });
});