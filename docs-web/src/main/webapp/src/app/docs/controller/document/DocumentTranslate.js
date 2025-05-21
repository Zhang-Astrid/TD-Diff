'use strict';

/**
 * Document translate controller.
 */
angular.module('docs').controller('DocumentTranslate', function($scope, $uibModalInstance, file, $translate) {
  $scope.file = file;
  $scope.targetLanguage = 'en';

  $scope.ok = function() {
    var confirmText = $translate.instant('ok');
    console.log('Confirm button text:', confirmText);
    if ($scope.targetLanguage) {
      $uibModalInstance.close($scope.targetLanguage);
    }
  };

  $scope.cancel = function() {
    var cancelText = $translate.instant('cancel');
    console.log('Cancel button text:', cancelText);
    $uibModalInstance.dismiss('cancel');
  };
});
