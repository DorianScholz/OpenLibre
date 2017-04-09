<?php
    $fileName = date('Y-m-d_H-i-s').'_openlibre_crash_report.txt';
    $file = fopen('reports/'.$fileName, 'w') or die('Could not create report file: ' . $fileName);

    /*
    // Output raw input data to file
    $json = file_get_contents('php://input');
    fwrite($file, $json);
    */

    // Outputs all POST parameters to a text file
    foreach($_POST as $key => $value) {
      $reportLine = $key." = ".$value."\n";
      fwrite($file, $reportLine) or die ('Could not write to report file ' . $reportLine);
    }

    fclose($file);
?>
