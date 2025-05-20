'use strict';

/**
 * Force refresh directive.
 */
angular.module('docs').directive('forceRefresh', function($window) {
  return {
    restrict: 'A',
    link: function() {
      // Force a refresh of the page to clear caches
      if (!$window.location.hash) {
        $window.location.hash = '#/';
      } else {
        $window.location.hash = '#/refresh';
        setTimeout(function() {
          $window.location.hash = '#/login';
        }, 100);
      }
    }
  };
}); 