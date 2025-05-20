'use strict';

/**
 * User service.
 */
angular.module('docs').factory('User', function(Restangular) {
  var userInfo = null;
  
  return {
    /**
     * Returns user info.
     * @param force If true, force reloading data
     */
    userInfo: function(force) {
      if (userInfo === null || force) {
        userInfo = Restangular.one('user').get();
      }
      return userInfo;
    },
    
    /**
     * Login an user.
     */
    login: function(user) {
      // return Restangular.one('user').post('login', user);
      return Restangular.one('user').post('login', user).then(function(response) {
        return response;
      }, function(error) {
        if(error.status === 403) {
          error.data = {
            type: 'LoginFailed',
            message: 'Invalid username or password'
          };
        }
        throw error;
      });
    },
    
    /**
     * Logout the current user.
     */
    logout: function() {
      return Restangular.one('user').post('logout', {});
    }
  }
});