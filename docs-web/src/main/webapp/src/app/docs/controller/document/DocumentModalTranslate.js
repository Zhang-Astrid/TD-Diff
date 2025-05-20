'use strict';

/**
 * Document translate modal controller.
 */
angular.module('docs').controller('DocumentModalTranslate', function($scope, $uibModalInstance, Restangular, document) {
  $scope.document = document;
  $scope.translating = false;
  $scope.error = null;
  $scope.contentType = 'description';
  $scope.translatedContent = '';

  // Available languages for translation
  $scope.languages = [
    { code: 'en', name: 'English' },
    { code: 'zh', name: 'Chinese' },
    { code: 'ja', name: 'Japanese' },
    { code: 'ko', name: 'Korean' },
    { code: 'fr', name: 'French' },
    { code: 'de', name: 'German' },
    { code: 'es', name: 'Spanish' },
    { code: 'ru', name: 'Russian' }
  ];
  
  // 默认选择中文作为目标语言
  $scope.targetLanguage = 'zh';

  /**
   * Translate the document.
   */
  $scope.translate = function() {
    console.log('Starting translation...');
    console.log('Target language selected:', $scope.targetLanguage);
    $scope.translating = true;
    $scope.error = null;
    $scope.translatedContent = '';
  
    var data = {
      targetLanguage: $scope.targetLanguage,
      contentType: $scope.contentType
    };
    console.log('Translation request data:', data);
  
    Restangular.one('document', document.id).all('translate').post(data)
      .then(function(response) {
        console.log('Translation response:', response);
        if (response && response.translated) {
          $scope.translatedContent = response.translated;
          console.log('Translation content set:', $scope.translatedContent);
        } else {
          console.error('No translation in response:', response);
          $scope.error = 'No translation received from server';
        }
      })
      .catch(function(error) {
        console.error('Translation error:', error);
        $scope.error = error.data ? error.data.message : 'An error occurred during translation';
      })
      .finally(function() {
        $scope.translating = false;
        console.log('Translation process completed');
      });
  };
}); 
// A1